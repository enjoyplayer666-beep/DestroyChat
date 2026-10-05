--[[
	DESTROY SIMULATOR — серверный скрипт
	Куда положить: ServerScriptService → Script (назови GameServer)

	Скрипт сам строит арену, спавнит блоки, считает монеты,
	сохраняет прогресс и раз в несколько минут запускает «Золотую лихорадку».
	Все настройки — в таблицах CONFIG и BLOCK_TYPES ниже.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local DataStoreService = game:GetService("DataStoreService")
local TweenService = game:GetService("TweenService")
local Lighting = game:GetService("Lighting")
local Debris = game:GetService("Debris")

------------------------------------------------------------------
-- НАСТРОЙКИ (меняй смело)
------------------------------------------------------------------
local CONFIG = {
	ArenaSize = 180, -- размер арены в студах
	MaxBlocks = 45, -- сколько блоков одновременно на карте
	SpawnInterval = 0.5, -- как часто появляется новый блок (сек)
	ClickDistance = 40, -- с какого расстояния можно бить блок
	HitCooldown = 0.1, -- защита от автокликера (сек между ударами)
	CritChance = 0.1, -- шанс крита (0.1 = 10%)
	CritMultiplier = 3, -- во сколько раз крит сильнее
	ComboWindow = 1.5, -- сколько секунд держится комбо между ударами
	ComboMax = 50, -- на комбо 50 и выше монеты x2
	RebirthBonus = 0.5, -- +50% монет за каждый ребёрт
	EventEvery = 180, -- «Золотая лихорадка» каждые N секунд
	EventDuration = 30, -- длительность лихорадки (сек)
	EventCoinMultiplier = 2, -- множитель монет во время лихорадки
	EventRareBoost = 4, -- во сколько раз чаще редкие блоки во время лихорадки
	AutosaveEvery = 60, -- автосохранение (сек)
	DataStoreName = "DestroySim_v1",
}

-- Виды блоков. weight — насколько часто появляется (больше = чаще).
local BLOCK_TYPES = {
	{
		name = "Ящик",
		size = Vector3.new(4, 4, 4),
		hp = 5,
		reward = 3,
		weight = 55,
		color = Color3.fromRGB(160, 110, 60),
		material = Enum.Material.WoodPlanks,
	},
	{
		name = "Камень",
		size = Vector3.new(5, 5, 5),
		hp = 25,
		reward = 15,
		weight = 25,
		color = Color3.fromRGB(120, 120, 125),
		material = Enum.Material.Slate,
	},
	{
		name = "Сейф",
		size = Vector3.new(5, 6, 5),
		hp = 80,
		reward = 60,
		weight = 12,
		color = Color3.fromRGB(70, 80, 95),
		material = Enum.Material.DiamondPlate,
	},
	{
		name = "Золотой блок",
		size = Vector3.new(6, 6, 6),
		hp = 250,
		reward = 300,
		weight = 6,
		color = Color3.fromRGB(255, 200, 40),
		material = Enum.Material.Neon,
		rare = true,
	},
	{
		name = "АЛМАЗНЫЙ БЛОК",
		size = Vector3.new(8, 8, 8),
		hp = 1500,
		reward = 2500,
		weight = 2,
		color = Color3.fromRGB(80, 230, 255),
		material = Enum.Material.Glass,
		rare = true,
		announce = true, -- сообщить всему серверу о появлении
	},
}
local GOLD_BLOCK = BLOCK_TYPES[4]

local COIN_STAT = "Монеты"
local REBIRTH_STAT = "Ребёрты"

local GOLD = Color3.fromRGB(255, 200, 40)
local RED = Color3.fromRGB(255, 80, 80)
local WHITE = Color3.new(1, 1, 1)

------------------------------------------------------------------
-- ФОРМУЛЫ
------------------------------------------------------------------
local function upgradeCost(power)
	return math.floor(20 * 1.35 ^ (power - 1))
end

local function rebirthCost(rebirths)
	return 5000 * (rebirths + 1) ^ 2
end

local function coinMultiplier(rebirths)
	return 1 + rebirths * CONFIG.RebirthBonus
end

local SUFFIXES = { "", "K", "M", "B", "T", "Qa", "Qi" }
local function abbreviate(n)
	n = math.floor(n)
	if n < 1000 then
		return tostring(n)
	end
	local i = 1
	while n >= 1000 and i < #SUFFIXES do
		n /= 1000
		i += 1
	end
	return string.format("%.1f%s", n, SUFFIXES[i])
end

------------------------------------------------------------------
-- REMOTE-СОБЫТИЯ (связь сервера и интерфейса игрока)
------------------------------------------------------------------
local remotes = Instance.new("Folder")
remotes.Name = "DestroyRemotes"

local buyUpgradeRemote = Instance.new("RemoteEvent")
buyUpgradeRemote.Name = "BuyUpgrade"
buyUpgradeRemote.Parent = remotes

local rebirthRemote = Instance.new("RemoteEvent")
rebirthRemote.Name = "Rebirth"
rebirthRemote.Parent = remotes

local announceRemote = Instance.new("RemoteEvent")
announceRemote.Name = "Announce"
announceRemote.Parent = remotes

remotes.Parent = ReplicatedStorage

------------------------------------------------------------------
-- АРЕНА
------------------------------------------------------------------
local SPAWN_POS = Vector3.new(0, 1, -(CONFIG.ArenaSize / 2 - 12))

local function makePart(props)
	local part = Instance.new("Part")
	part.Anchored = true
	part.TopSurface = Enum.SurfaceType.Smooth
	part.BottomSurface = Enum.SurfaceType.Smooth
	for key, value in props do
		if key ~= "Parent" then
			part[key] = value
		end
	end
	part.Parent = props.Parent
	return part
end

local arena = Instance.new("Folder")
arena.Name = "Arena"

makePart({
	Name = "Floor",
	Size = Vector3.new(CONFIG.ArenaSize, 2, CONFIG.ArenaSize),
	Position = Vector3.new(0, -0.5, 0), -- верх пола на высоте 0.5
	Material = Enum.Material.Concrete,
	Color = Color3.fromRGB(55, 55, 65),
	Parent = arena,
})

local half = CONFIG.ArenaSize / 2
local walls = {
	{ Vector3.new(0, 4, half), Vector3.new(CONFIG.ArenaSize, 8, 2) },
	{ Vector3.new(0, 4, -half), Vector3.new(CONFIG.ArenaSize, 8, 2) },
	{ Vector3.new(half, 4, 0), Vector3.new(2, 8, CONFIG.ArenaSize) },
	{ Vector3.new(-half, 4, 0), Vector3.new(2, 8, CONFIG.ArenaSize) },
}
for _, wall in walls do
	makePart({
		Name = "Wall",
		Position = wall[1],
		Size = wall[2],
		Material = Enum.Material.Neon,
		Color = Color3.fromRGB(255, 60, 90),
		Transparency = 0.4,
		Parent = arena,
	})
end

local spawnLocation = Instance.new("SpawnLocation")
spawnLocation.Name = "DestroySpawn"
spawnLocation.Anchored = true
spawnLocation.Size = Vector3.new(14, 1, 14)
spawnLocation.Position = SPAWN_POS
spawnLocation.Material = Enum.Material.Neon
spawnLocation.Color = Color3.fromRGB(80, 255, 140)
spawnLocation.Duration = 0 -- без силового поля
spawnLocation.Neutral = true
spawnLocation.Parent = arena

arena.Parent = workspace

local blocksFolder = Instance.new("Folder")
blocksFolder.Name = "Destructibles"
blocksFolder.Parent = workspace

local effectsFolder = Instance.new("Folder")
effectsFolder.Name = "Effects"
effectsFolder.Parent = workspace

------------------------------------------------------------------
-- ЭФФЕКТЫ
------------------------------------------------------------------
local function popup(position, text, color, big)
	local anchor = makePart({
		Name = "Popup",
		Size = Vector3.new(0.2, 0.2, 0.2),
		Position = position,
		Transparency = 1,
		CanCollide = false,
		CanQuery = false,
		CanTouch = false,
		Parent = effectsFolder,
	})

	local gui = Instance.new("BillboardGui")
	gui.Size = big and UDim2.fromOffset(240, 60) or UDim2.fromOffset(140, 40)
	gui.AlwaysOnTop = true
	gui.MaxDistance = 100
	gui.LightInfluence = 0
	gui.Parent = anchor

	local label = Instance.new("TextLabel")
	label.BackgroundTransparency = 1
	label.Size = UDim2.fromScale(1, 1)
	label.Font = Enum.Font.GothamBlack
	label.TextScaled = true
	label.TextColor3 = color
	label.TextStrokeTransparency = 0
	label.Text = text
	label.Parent = gui

	TweenService:Create(anchor, TweenInfo.new(0.9, Enum.EasingStyle.Quad, Enum.EasingDirection.Out), {
		Position = position + Vector3.new(0, 4, 0),
	}):Play()
	TweenService:Create(label, TweenInfo.new(0.5, Enum.EasingStyle.Linear, Enum.EasingDirection.In, 0, false, 0.4), {
		TextTransparency = 1,
		TextStrokeTransparency = 1,
	}):Play()
	Debris:AddItem(anchor, 1)
end

local function shatter(part, blockType)
	local pieces = blockType.rare and 16 or 8
	for _ = 1, pieces do
		local offset = Vector3.new(
			(math.random() - 0.5) * part.Size.X,
			(math.random() - 0.5) * part.Size.Y,
			(math.random() - 0.5) * part.Size.Z
		)
		local piece = makePart({
			Name = "Piece",
			Anchored = false,
			Size = part.Size / 3 * (0.6 + math.random() * 0.6),
			CFrame = part.CFrame * CFrame.new(offset),
			Color = part.Color,
			Material = part.Material,
			CanQuery = false,
			CanTouch = false,
			Massless = true,
			Parent = effectsFolder,
		})
		piece.AssemblyLinearVelocity = Vector3.new(
			(math.random() - 0.5) * 50,
			25 + math.random() * 30,
			(math.random() - 0.5) * 50
		)
		piece.AssemblyAngularVelocity = Vector3.new(math.random() * 10, math.random() * 10, math.random() * 10)
		Debris:AddItem(piece, 2.5)
	end
end

local function announce(text, color, target)
	if target then
		announceRemote:FireClient(target, text, color or WHITE)
	else
		announceRemote:FireAllClients(text, color or WHITE)
	end
end

------------------------------------------------------------------
-- ДАННЫЕ ИГРОКОВ
------------------------------------------------------------------
local store = nil
do
	local ok, err = pcall(function()
		store = DataStoreService:GetDataStore(CONFIG.DataStoreName)
	end)
	if not ok then
		warn("[DestroySim] Сохранения выключены: " .. tostring(err))
	end
end

local function getStat(player, name)
	local stats = player:FindFirstChild("leaderstats")
	return stats and stats:FindFirstChild(name)
end

local function refreshAttributes(player)
	local rebirths = getStat(player, REBIRTH_STAT)
	if not rebirths then
		return
	end
	local power = player:GetAttribute("Power") or 1
	player:SetAttribute("UpgradeCost", upgradeCost(power))
	player:SetAttribute("RebirthCost", rebirthCost(rebirths.Value))
	player:SetAttribute("CoinMultiplier", coinMultiplier(rebirths.Value))
end

local function addCoins(player, amount)
	local coins = getStat(player, COIN_STAT)
	if coins then
		coins.Value += amount
	end
end

local function loadData(player)
	local saved = nil
	local loaded = true
	if store then
		local ok, result = pcall(function()
			return store:GetAsync("player_" .. player.UserId)
		end)
		if ok then
			saved = result
		else
			loaded = false
			warn("[DestroySim] Не удалось загрузить данные " .. player.Name .. ": " .. tostring(result))
		end
	end
	saved = saved or {}
	return {
		coins = saved.coins or 0,
		power = saved.power or 1,
		rebirths = saved.rebirths or 0,
	}, loaded
end

local function saveData(player)
	-- Если загрузка не удалась, не сохраняем, чтобы не затереть старый прогресс нулями
	if not store or not player:GetAttribute("DataLoaded") then
		return
	end
	local coins = getStat(player, COIN_STAT)
	local rebirths = getStat(player, REBIRTH_STAT)
	if not coins or not rebirths then
		return
	end
	local data = {
		coins = coins.Value,
		power = player:GetAttribute("Power") or 1,
		rebirths = rebirths.Value,
	}
	local ok, err = pcall(function()
		store:SetAsync("player_" .. player.UserId, data)
	end)
	if not ok then
		warn("[DestroySim] Не удалось сохранить " .. player.Name .. ": " .. tostring(err))
	end
end

local lastHit = {}
local combos = {}

local function onPlayerAdded(player)
	local data, loaded = loadData(player)

	local leaderstats = Instance.new("Folder")
	leaderstats.Name = "leaderstats"

	local coins = Instance.new("IntValue")
	coins.Name = COIN_STAT
	coins.Value = data.coins
	coins.Parent = leaderstats

	local rebirths = Instance.new("IntValue")
	rebirths.Name = REBIRTH_STAT
	rebirths.Value = data.rebirths
	rebirths.Parent = leaderstats

	player:SetAttribute("Power", data.power)
	player:SetAttribute("Combo", 0)
	player:SetAttribute("DataLoaded", loaded)
	leaderstats.Parent = player
	refreshAttributes(player)
end

Players.PlayerAdded:Connect(onPlayerAdded)
for _, player in Players:GetPlayers() do
	task.spawn(onPlayerAdded, player)
end

Players.PlayerRemoving:Connect(function(player)
	saveData(player)
	lastHit[player] = nil
	combos[player] = nil
end)

game:BindToClose(function()
	for _, player in Players:GetPlayers() do
		task.spawn(saveData, player)
	end
	task.wait(3)
end)

task.spawn(function()
	while true do
		task.wait(CONFIG.AutosaveEvery)
		for _, player in Players:GetPlayers() do
			task.spawn(saveData, player)
		end
	end
end)

------------------------------------------------------------------
-- МАГАЗИН И РЕБЁРТЫ
------------------------------------------------------------------
buyUpgradeRemote.OnServerEvent:Connect(function(player, mode)
	local coins = getStat(player, COIN_STAT)
	if not coins then
		return
	end
	local power = player:GetAttribute("Power") or 1
	local bought = 0
	local limit = mode == "max" and 1000 or 1
	while bought < limit and coins.Value >= upgradeCost(power) do
		coins.Value -= upgradeCost(power)
		power += 1
		bought += 1
	end
	if bought == 0 then
		announce("Не хватает монет!", RED, player)
		return
	end
	player:SetAttribute("Power", power)
	refreshAttributes(player)
	if bought > 1 then
		announce("⚡ Сила +" .. bought .. "!", GOLD, player)
	end
end)

rebirthRemote.OnServerEvent:Connect(function(player)
	local coins = getStat(player, COIN_STAT)
	local rebirths = getStat(player, REBIRTH_STAT)
	if not coins or not rebirths then
		return
	end
	local cost = rebirthCost(rebirths.Value)
	if coins.Value < cost then
		announce("Для ребёрта нужно 💰 " .. abbreviate(cost), RED, player)
		return
	end
	coins.Value = 0
	rebirths.Value += 1
	player:SetAttribute("Power", 1)
	refreshAttributes(player)
	announce("🔁 " .. player.DisplayName .. " сделал ребёрт #" .. rebirths.Value .. "!", Color3.fromRGB(190, 120, 255))
end)

------------------------------------------------------------------
-- БЛОКИ
------------------------------------------------------------------
local eventActive = false
local blockData = {} -- [Part] = { hp, maxHp, blockType, size, damageBy, fill, hpText }

local function pickType()
	local boost = eventActive and CONFIG.EventRareBoost or 1
	local total = 0
	for _, blockType in BLOCK_TYPES do
		total += blockType.weight * (blockType.rare and boost or 1)
	end
	local roll = math.random() * total
	for _, blockType in BLOCK_TYPES do
		roll -= blockType.weight * (blockType.rare and boost or 1)
		if roll <= 0 then
			return blockType
		end
	end
	return BLOCK_TYPES[1]
end

local function findSpot(size)
	local limit = math.floor(CONFIG.ArenaSize / 2 - 8)
	for _ = 1, 25 do
		local flat = Vector3.new(math.random(-limit, limit), 0, math.random(-limit, limit))
		local farFromSpawn = (flat - Vector3.new(SPAWN_POS.X, 0, SPAWN_POS.Z)).Magnitude > 18
			and flat.Magnitude > 14 -- центр тоже свободен (там стоит стандартный спавн)
		local free = farFromSpawn
		if free then
			for part in blockData do
				if (Vector3.new(part.Position.X, 0, part.Position.Z) - flat).Magnitude < 9 then
					free = false
					break
				end
			end
		end
		if free then
			return flat + Vector3.new(0, 0.5 + size.Y / 2, 0)
		end
	end
	return nil
end

local function updateBar(data)
	local ratio = math.clamp(data.hp / data.maxHp, 0, 1)
	data.fill.Size = UDim2.fromScale(ratio, 1)
	data.fill.BackgroundColor3 = Color3.fromRGB(255, 70, 70):Lerp(Color3.fromRGB(70, 230, 100), ratio)
	data.hpText.Text = abbreviate(data.hp) .. " / " .. abbreviate(data.maxHp)
end

local function makeHealthBar(part, blockType)
	local gui = Instance.new("BillboardGui")
	gui.Name = "HealthBar"
	gui.Size = UDim2.fromOffset(130, 40)
	gui.StudsOffset = Vector3.new(0, blockType.size.Y / 2 + 2, 0)
	gui.MaxDistance = 70
	gui.LightInfluence = 0

	local title = Instance.new("TextLabel")
	title.BackgroundTransparency = 1
	title.Size = UDim2.fromScale(1, 0.5)
	title.Font = Enum.Font.GothamBold
	title.TextScaled = true
	title.TextColor3 = blockType.rare and blockType.color or WHITE
	title.TextStrokeTransparency = 0.3
	title.Text = blockType.name
	title.Parent = gui

	local back = Instance.new("Frame")
	back.Position = UDim2.fromScale(0, 0.55)
	back.Size = UDim2.fromScale(1, 0.45)
	back.BackgroundColor3 = Color3.fromRGB(30, 30, 30)
	back.BorderSizePixel = 0
	back.Parent = gui
	Instance.new("UICorner").Parent = back

	local fill = Instance.new("Frame")
	fill.Size = UDim2.fromScale(1, 1)
	fill.BorderSizePixel = 0
	fill.Parent = back
	Instance.new("UICorner").Parent = fill

	local hpText = Instance.new("TextLabel")
	hpText.BackgroundTransparency = 1
	hpText.Size = UDim2.fromScale(1, 1)
	hpText.ZIndex = 2
	hpText.Font = Enum.Font.GothamBold
	hpText.TextScaled = true
	hpText.TextColor3 = WHITE
	hpText.TextStrokeTransparency = 0.4
	hpText.Parent = back

	gui.Parent = part
	return fill, hpText
end

local function destroyBlock(part, data)
	data.dead = true
	blockData[part] = nil

	local totalDamage = 0
	for _, damage in data.damageBy do
		totalDamage += damage
	end

	local eventMult = eventActive and CONFIG.EventCoinMultiplier or 1
	local topPlayer, topReward = nil, 0
	for player, damage in data.damageBy do
		local rebirths = getStat(player, REBIRTH_STAT)
		if player.Parent and rebirths then
			-- Награда делится между всеми, кто бил блок, по нанесённому урону
			local combo = combos[player]
			local comboCount = (combo and os.clock() - combo.last <= CONFIG.ComboWindow) and combo.count or 0
			local comboMult = 1 + math.min(comboCount, CONFIG.ComboMax) / CONFIG.ComboMax
			local share = damage / totalDamage
			local reward = data.blockType.reward * share * coinMultiplier(rebirths.Value) * comboMult * eventMult
			reward = math.max(1, math.floor(reward))
			addCoins(player, reward)
			if reward > topReward then
				topPlayer, topReward = player, reward
			end
		end
	end

	if topPlayer then
		popup(part.Position + Vector3.new(0, data.size.Y / 2, 0), "+" .. abbreviate(topReward) .. " 💰", GOLD, true)
		if data.blockType.announce then
			announce(
				"💎 " .. topPlayer.DisplayName .. " разбил " .. data.blockType.name .. " и получил 💰 " .. abbreviate(topReward) .. "!",
				data.blockType.color
			)
		end
	end

	shatter(part, data.blockType)
	part:Destroy()
end

local function hitBlock(player, part)
	local data = blockData[part]
	if not data or data.dead then
		return
	end

	local now = os.clock()
	if lastHit[player] and now - lastHit[player] < CONFIG.HitCooldown then
		return
	end
	lastHit[player] = now

	local combo = combos[player]
	if combo and now - combo.last <= CONFIG.ComboWindow then
		combo.count += 1
	else
		combo = { count = 1, last = now }
		combos[player] = combo
	end
	combo.last = now
	player:SetAttribute("Combo", combo.count)

	local power = player:GetAttribute("Power") or 1
	local crit = math.random() < CONFIG.CritChance
	local damage = power * (crit and CONFIG.CritMultiplier or 1)
	local dealt = math.min(damage, data.hp)
	data.hp -= dealt
	data.damageBy[player] = (data.damageBy[player] or 0) + dealt

	local jitter = Vector3.new((math.random() - 0.5) * 3, data.size.Y / 2, (math.random() - 0.5) * 3)
	if crit then
		popup(part.Position + jitter, "КРИТ! -" .. abbreviate(damage), Color3.fromRGB(255, 120, 40), true)
	else
		popup(part.Position + jitter, "-" .. abbreviate(damage), WHITE, false)
	end

	-- «Сжатие» блока при ударе
	part.Size = data.size * 0.88
	TweenService:Create(part, TweenInfo.new(0.12, Enum.EasingStyle.Back, Enum.EasingDirection.Out), {
		Size = data.size,
	}):Play()

	if data.hp <= 0 then
		destroyBlock(part, data)
	else
		updateBar(data)
	end
end

local function spawnBlock(blockType)
	local position = findSpot(blockType.size)
	if not position then
		return
	end

	local part = makePart({
		Name = blockType.name,
		Size = blockType.size * 0.2,
		CFrame = CFrame.new(position) * CFrame.Angles(0, math.rad(math.random(0, 359)), 0),
		Color = blockType.color,
		Material = blockType.material,
	})

	if blockType.rare then
		local light = Instance.new("PointLight")
		light.Color = blockType.color
		light.Brightness = 2
		light.Range = 16
		light.Parent = part

		local sparkles = Instance.new("Sparkles")
		sparkles.SparkleColor = blockType.color
		sparkles.Parent = part
	end

	local fill, hpText = makeHealthBar(part, blockType)

	local click = Instance.new("ClickDetector")
	click.MaxActivationDistance = CONFIG.ClickDistance
	click.Parent = part

	local data = {
		hp = blockType.hp,
		maxHp = blockType.hp,
		blockType = blockType,
		size = blockType.size,
		damageBy = {},
		fill = fill,
		hpText = hpText,
	}
	blockData[part] = data
	updateBar(data)

	click.MouseClick:Connect(function(player)
		hitBlock(player, part)
	end)

	part.Parent = blocksFolder
	TweenService:Create(part, TweenInfo.new(0.35, Enum.EasingStyle.Back, Enum.EasingDirection.Out), {
		Size = blockType.size,
	}):Play()

	if blockType.announce then
		announce("✨ Появился " .. blockType.name .. "! Беги ломать! ✨", blockType.color)
	end
end

local function countBlocks()
	local count = 0
	for _ in blockData do
		count += 1
	end
	return count
end

-- Сразу заполняем арену
for _ = 1, CONFIG.MaxBlocks do
	spawnBlock(pickType())
end

task.spawn(function()
	while true do
		task.wait(CONFIG.SpawnInterval)
		if countBlocks() < CONFIG.MaxBlocks then
			spawnBlock(pickType())
		end
	end
end)

------------------------------------------------------------------
-- ЗОЛОТАЯ ЛИХОРАДКА
------------------------------------------------------------------
local tint = Instance.new("ColorCorrectionEffect")
tint.Name = "GoldRushTint"
tint.Parent = Lighting

task.spawn(function()
	while true do
		workspace:SetAttribute("EventActive", false)
		workspace:SetAttribute("NextEventAt", workspace:GetServerTimeNow() + CONFIG.EventEvery)
		task.wait(CONFIG.EventEvery)

		eventActive = true
		workspace:SetAttribute("EventActive", true)
		workspace:SetAttribute("EventMultiplier", CONFIG.EventCoinMultiplier)
		workspace:SetAttribute("EventEndsAt", workspace:GetServerTimeNow() + CONFIG.EventDuration)
		announce("🔥 ЗОЛОТАЯ ЛИХОРАДКА! Монеты x" .. CONFIG.EventCoinMultiplier .. "! 🔥", GOLD)
		TweenService:Create(tint, TweenInfo.new(1), {
			TintColor = Color3.fromRGB(255, 225, 160),
			Saturation = 0.35,
		}):Play()

		for _ = 1, 6 do
			spawnBlock(GOLD_BLOCK)
			task.wait(0.3)
		end

		task.wait(CONFIG.EventDuration)
		eventActive = false
		TweenService:Create(tint, TweenInfo.new(1), {
			TintColor = WHITE,
			Saturation = 0,
		}):Play()
		announce("Лихорадка закончилась. Копи силу до следующей!", WHITE)
	end
end)

print("[DestroySim] Сервер запущен! Ломай блоки 💥")
