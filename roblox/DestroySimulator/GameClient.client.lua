--[[
	DESTROY SIMULATOR — клиентский скрипт (интерфейс игрока)
	Куда положить: StarterPlayer → StarterPlayerScripts → LocalScript (назови GameClient)

	Рисует монеты, кнопки, окна (кирки, питомцы, яйца, миры, трейды),
	открытие яиц, питомцев рядом с игроками и освещение каждого мира.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")
local RunService = game:GetService("RunService")
local Lighting = game:GetService("Lighting")
local SoundService = game:GetService("SoundService")
local Debris = game:GetService("Debris")

local V = Vector3.new
local C = Color3.fromRGB

local player = Players.LocalPlayer
local remotes = ReplicatedStorage:WaitForChild("DestroyRemotes")
local shopRemote = remotes:WaitForChild("Shop")
local hitRemote = remotes:WaitForChild("Hit")
local announceRemote = remotes:WaitForChild("Announce")
local petRemote = remotes:WaitForChild("Pets")
local inventoryRemote = remotes:WaitForChild("Inventory")
local openEggRemote = remotes:WaitForChild("OpenEgg")
local eggResultRemote = remotes:WaitForChild("EggOpened")
local worldRemote = remotes:WaitForChild("World")
local tradeRemote = remotes:WaitForChild("Trade")
local INFO = remotes:WaitForChild("GetInfo"):InvokeServer()
local petModels = ReplicatedStorage:WaitForChild("PetModels")
local blocksFolder = workspace:WaitForChild("Destructibles")

local leaderstats = player:WaitForChild("leaderstats")
local coinsValue = leaderstats:WaitForChild("Монеты")
local rebirthsValue = leaderstats:WaitForChild("Ребёрты")

local WHITE = Color3.new(1, 1, 1)
local GOLD = C(255, 200, 40)
local GREEN = C(60, 200, 90)
local BLUE = C(60, 140, 230)
local PURPLE = C(150, 80, 230)
local GRAY = C(90, 90, 100)
local RED = C(220, 70, 70)
local PANEL = C(28, 28, 38)
local CARD = C(45, 45, 60)
local SOFT = C(190, 190, 205)

------------------------------------------------------------------
-- ПОМОЩНИКИ
------------------------------------------------------------------
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

local function percent(value)
	return abbreviate(value * 100 + 0.5) .. "%"
end

local function formatMultiplier(value)
	if value >= 1000 then
		return abbreviate(value)
	end
	local text = string.format("%.2f", value):gsub("%.?0+$", "")
	return text
end

local function create(className, props)
	local instance = Instance.new(className)
	for key, value in props do
		if key ~= "Parent" then
			instance[key] = value
		end
	end
	instance.Parent = props.Parent
	return instance
end

local function with(defaults, props)
	local result = table.clone(defaults)
	for key, value in props do
		result[key] = value
	end
	return result
end

local function round(parent, radius)
	create("UICorner", { CornerRadius = UDim.new(0, radius), Parent = parent })
end

local function stroke(parent, color, thickness)
	create("UIStroke", {
		Color = color,
		Thickness = thickness or 2,
		ApplyStrokeMode = Enum.ApplyStrokeMode.Border,
		Parent = parent,
	})
end

local function textStroke(parent, thickness)
	return create("UIStroke", {
		Color = C(15, 15, 25),
		Thickness = thickness or 1.5,
		ApplyStrokeMode = Enum.ApplyStrokeMode.Contextual,
		Parent = parent,
	})
end

-- Градиент сверху вниз: делает плоский цвет «объёмным»
local function gradient(parent, top, bottom)
	create("UIGradient", {
		Color = ColorSequence.new(top, bottom),
		Rotation = 90,
		Parent = parent,
	})
end

------------------------------------------------------------------
-- ЗВУКИ (можно заменить на любые звуки из Toolbox: rbxassetid://ID)
------------------------------------------------------------------
local SOUNDS = {
	click = { id = "rbxasset://sounds/clickfast.wav", volume = 0.4 },
	open = { id = "rbxasset://sounds/clickfast.wav", volume = 0.3, speed = 0.8 },
	hit = { id = "rbxasset://sounds/action_jump_land.mp3", volume = 0.5, speed = 1.3 },
	coin = { id = "rbxasset://sounds/electronicpingshort.wav", volume = 0.2, speed = 1.6 },
	reveal = { id = "rbxasset://sounds/electronicpingshort.wav", volume = 0.6, speed = 0.8 },
}
local lastPlayed = {}

local function playSound(name)
	local def = SOUNDS[name]
	local now = os.clock()
	if not def or (lastPlayed[name] and now - lastPlayed[name] < 0.06) then
		return
	end
	lastPlayed[name] = now
	local sound = Instance.new("Sound")
	sound.SoundId = def.id
	sound.Volume = def.volume
	sound.PlaybackSpeed = (def.speed or 1) * (0.95 + math.random() * 0.1)
	sound.Parent = SoundService
	SoundService:PlayLocalSound(sound)
	Debris:AddItem(sound, 3)
end

local function pad(parent, pixels)
	create("UIPadding", {
		PaddingTop = UDim.new(0, pixels),
		PaddingBottom = UDim.new(0, pixels),
		PaddingLeft = UDim.new(0, pixels),
		PaddingRight = UDim.new(0, pixels),
		Parent = parent,
	})
end

local LABEL = {
	BackgroundTransparency = 1,
	Font = Enum.Font.GothamBold,
	TextScaled = true,
	TextColor3 = WHITE,
}
local function label(props)
	local text = create("TextLabel", with(LABEL, props))
	-- жирный текст с тёмной обводкой, как в популярных симуляторах
	if text.Font ~= Enum.Font.Gotham then
		textStroke(text, 1.5)
	end
	return text
end

local BUTTON = {
	BackgroundColor3 = GREEN,
	Font = Enum.Font.GothamBlack,
	TextScaled = true,
	TextColor3 = WHITE,
	AutoButtonColor = true,
	Text = "",
}
local function makeButton(props, padding)
	local button = create("TextButton", with(BUTTON, props))
	round(button, 10)
	pad(button, padding or 6)
	gradient(button, WHITE, C(170, 170, 185))
	stroke(button, C(15, 15, 25), 2.5)
	textStroke(button, 1.5)
	-- кнопка «пружинит» при наведении и нажатии
	local scale = create("UIScale", { Parent = button })
	local function scaleTo(value)
		TweenService:Create(scale, TweenInfo.new(0.1), { Scale = value }):Play()
	end
	button.MouseEnter:Connect(function()
		scaleTo(1.05)
	end)
	button.MouseLeave:Connect(function()
		scaleTo(1)
	end)
	button.MouseButton1Down:Connect(function()
		scaleTo(0.92)
	end)
	button.MouseButton1Up:Connect(function()
		scaleTo(1.05)
	end)
	button.Activated:Connect(function()
		playSound("click")
	end)
	return button
end

local function scrolling(props)
	return create(
		"ScrollingFrame",
		with({
			BackgroundTransparency = 1,
			BorderSizePixel = 0,
			ScrollBarThickness = 6,
			CanvasSize = UDim2.new(),
			AutomaticCanvasSize = Enum.AutomaticSize.Y,
			ScrollingDirection = Enum.ScrollingDirection.Y,
		}, props)
	)
end

local function clear(container)
	for _, child in container:GetChildren() do
		if child:IsA("GuiObject") then
			child:Destroy()
		end
	end
end

local function unlockedWorlds()
	local set = {}
	for id in string.gmatch(player:GetAttribute("Worlds") or "1", "%d+") do
		set[tonumber(id)] = true
	end
	return set
end

-- Маленькое 3D-окошко с питомцем
local function petViewport(kind, parent, props)
	local viewport = create(
		"ViewportFrame",
		with({
			BackgroundTransparency = 1,
			Size = UDim2.fromScale(1, 1),
			Ambient = C(200, 200, 200),
			LightColor = WHITE,
			Parent = parent,
		}, props)
	)
	local template = petModels:FindFirstChild(kind)
	if template then
		local model = template:Clone()
		model:PivotTo(CFrame.new())
		model.Parent = viewport
		local camera = Instance.new("Camera")
		camera.FieldOfView = 40
		camera.Parent = viewport
		viewport.CurrentCamera = camera
		local center, size = model:GetBoundingBox()
		local distance = size.Magnitude * 1.4
		camera.CFrame = CFrame.lookAt(center.Position + V(0.6, 0.4, -1).Unit * distance, center.Position)
	end
	return viewport
end

local function petCard(kind, parent, order, equipped)
	local info = INFO.pets[kind]
	local rarity = INFO.rarities[info.rarity]
	local card = create("TextButton", {
		LayoutOrder = order,
		Text = "",
		AutoButtonColor = true,
		BackgroundColor3 = equipped and C(45, 85, 55) or CARD,
		Parent = parent,
	})
	round(card, 10)
	stroke(card, rarity.color, 2.5)
	gradient(card, rarity.color:Lerp(WHITE, 0.55), C(150, 150, 170))
	petViewport(kind, card, { Size = UDim2.fromScale(1, 0.62) })
	label({
		Position = UDim2.fromScale(0.05, 0.62),
		Size = UDim2.fromScale(0.9, 0.19),
		Text = kind,
		TextColor3 = rarity.color,
		Parent = card,
	})
	label({
		Position = UDim2.fromScale(0.05, 0.81),
		Size = UDim2.fromScale(0.9, 0.16),
		Text = "+" .. percent(info.bonus) .. " 💰",
		Font = Enum.Font.GothamBlack,
		Parent = card,
	})
	if equipped then
		label({
			Position = UDim2.fromOffset(4, 2),
			Size = UDim2.fromOffset(24, 24),
			Text = "✔",
			TextColor3 = GREEN,
			Font = Enum.Font.GothamBlack,
			Parent = card,
		})
	end
	return card
end

------------------------------------------------------------------
-- ОСНОВНОЙ ИНТЕРФЕЙС
------------------------------------------------------------------
local gui = create("ScreenGui", {
	Name = "DestroyHUD",
	ResetOnSpawn = false,
	ZIndexBehavior = Enum.ZIndexBehavior.Sibling,
	Parent = player:WaitForChild("PlayerGui"),
})

-- Монеты сверху по центру
local coinsPanel = create("Frame", {
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 8),
	Size = UDim2.fromOffset(280, 56),
	BackgroundColor3 = C(45, 45, 70),
	Parent = gui,
})
round(coinsPanel, 14)
pad(coinsPanel, 6)
gradient(coinsPanel, WHITE, C(140, 140, 170))
stroke(coinsPanel, GOLD, 3)
local coinsScale = create("UIScale", { Parent = coinsPanel })
local coinsLabel = label({
	Size = UDim2.fromScale(1, 1),
	Font = Enum.Font.GothamBlack,
	TextColor3 = GOLD,
	Text = "💰 0",
	Parent = coinsPanel,
})

local statsLabel = label({
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 70),
	Size = UDim2.fromOffset(400, 24),
	Parent = gui,
})

local eventLabel = label({
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 98),
	Size = UDim2.fromOffset(400, 28),
	Font = Enum.Font.GothamBlack,
	Parent = gui,
})

-- Кнопки прокачки слева
local sideButtons = create("Frame", {
	AnchorPoint = Vector2.new(0, 0.5),
	Position = UDim2.new(0, 12, 0.5, 0),
	Size = UDim2.fromOffset(210, 212),
	BackgroundTransparency = 1,
	Parent = gui,
})
create("UIListLayout", { Padding = UDim.new(0, 8), SortOrder = Enum.SortOrder.LayoutOrder, Parent = sideButtons })

local function sideButton(order)
	local button = makeButton({ LayoutOrder = order, Size = UDim2.new(1, 0, 0, 65), BackgroundColor3 = GRAY }, 8)
	button.Parent = sideButtons
	return button
end
local levelButton = sideButton(1)
local levelMaxButton = sideButton(2)
local rebirthButton = sideButton(3)

-- Меню снизу
local menu = create("Frame", {
	AnchorPoint = Vector2.new(0.5, 1),
	Position = UDim2.new(0.5, 0, 1, -82),
	Size = UDim2.fromOffset(560, 58),
	BackgroundTransparency = 1,
	Parent = gui,
})
create("UIListLayout", {
	FillDirection = Enum.FillDirection.Horizontal,
	HorizontalAlignment = Enum.HorizontalAlignment.Center,
	Padding = UDim.new(0, 8),
	SortOrder = Enum.SortOrder.LayoutOrder,
	Parent = menu,
})
local function menuButton(order, text, color)
	local button = makeButton({ LayoutOrder = order, Size = UDim2.fromOffset(104, 58), BackgroundColor3 = color, Text = text }, 6)
	button.Parent = menu
	return button
end
local pickaxesMenu = menuButton(1, "⛏\nКирки", C(200, 120, 40))
local petsMenu = menuButton(2, "🐾\nПитомцы", C(220, 90, 150))
local eggsMenu = menuButton(3, "🥚\nЯйца", C(230, 180, 40))
local worldsMenu = menuButton(4, "🌍\nМиры", BLUE)
local tradeMenu = menuButton(5, "🤝\nТрейд", PURPLE)

-- Комбо
local comboLabel = label({
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.72),
	Size = UDim2.fromOffset(320, 60),
	Font = Enum.Font.GothamBlack,
	Visible = false,
	Parent = gui,
})
local comboScale = create("UIScale", { Parent = comboLabel })
comboLabel:FindFirstChildOfClass("UIStroke").Thickness = 3

-- Большие объявления
local banner = label({
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.25),
	Size = UDim2.new(0.8, 0, 0, 52),
	Font = Enum.Font.GothamBlack,
	TextTransparency = 1,
	ZIndex = 30,
	Parent = gui,
})
local bannerScale = create("UIScale", { Parent = banner })
local bannerStroke = banner:FindFirstChildOfClass("UIStroke")
bannerStroke.Thickness = 3
bannerStroke.Transparency = 1

------------------------------------------------------------------
-- ОКНА
------------------------------------------------------------------
local camera = workspace.CurrentCamera
local windows = {}
local openName = nil

local function fitScale()
	local size = camera.ViewportSize
	return math.clamp(math.min((size.X - 20) / 640, (size.Y - 150) / 440), 0.5, 1)
end

local function closeWindows()
	local previous = openName
	openName = nil
	for _, window in windows do
		window.frame.Visible = false
	end
	if previous and windows[previous].onClose then
		windows[previous].onClose()
	end
end

local function openWindow(name)
	if openName ~= name then
		closeWindows()
	end
	openName = name
	local window = windows[name]
	local target = fitScale()
	if not window.frame.Visible then
		window.scale.Scale = target * 0.8
		TweenService:Create(window.scale, TweenInfo.new(0.25, Enum.EasingStyle.Back, Enum.EasingDirection.Out), { Scale = target }):Play()
		playSound("open")
	end
	window.frame.Visible = true
	window.render()
end

local function toggleWindow(name)
	if openName == name then
		closeWindows()
	else
		openWindow(name)
	end
end

local function makeWindow(name, title, accent)
	local frame = create("Frame", {
		Name = name,
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.new(0.5, 0, 0.5, -20),
		Size = UDim2.fromOffset(620, 420),
		BackgroundColor3 = C(40, 42, 62),
		Visible = false,
		ZIndex = 5,
		Parent = gui,
	})
	round(frame, 16)
	stroke(frame, C(15, 15, 25), 3)
	gradient(frame, WHITE, C(150, 150, 175))
	local scale = create("UIScale", { Parent = frame })
	-- цветная шапка окна
	local header = create("Frame", { Size = UDim2.new(1, 0, 0, 54), BackgroundColor3 = accent, BorderSizePixel = 0, Parent = frame })
	round(header, 16)
	gradient(header, WHITE, C(170, 170, 185))
	create("Frame", {
		Position = UDim2.fromOffset(0, 38),
		Size = UDim2.new(1, 0, 0, 16),
		BackgroundColor3 = accent:Lerp(C(0, 0, 0), 0.25),
		BorderSizePixel = 0,
		Parent = header,
	})
	local titleLabel = label({
		Position = UDim2.fromOffset(16, 10),
		Size = UDim2.new(1, -80, 0, 34),
		Text = title,
		TextXAlignment = Enum.TextXAlignment.Left,
		Font = Enum.Font.GothamBlack,
		Parent = frame,
	})
	local close = makeButton({
		AnchorPoint = Vector2.new(1, 0),
		Position = UDim2.new(1, -10, 0, 10),
		Size = UDim2.fromOffset(36, 36),
		BackgroundColor3 = RED,
		Text = "✕",
	}, 6)
	close.Parent = frame
	local body = create("Frame", {
		Position = UDim2.fromOffset(12, 64),
		Size = UDim2.new(1, -24, 1, -76),
		BackgroundTransparency = 1,
		Parent = frame,
	})
	local window = { frame = frame, body = body, title = titleLabel, scale = scale, render = function() end, onClose = nil }
	windows[name] = window
	close.Activated:Connect(closeWindows)
	return window
end

local function makeRow(parent, order, height)
	local row = create("Frame", {
		LayoutOrder = order,
		Size = UDim2.new(1, -10, 0, height),
		BackgroundColor3 = C(58, 60, 84),
		Parent = parent,
	})
	round(row, 10)
	gradient(row, WHITE, C(175, 175, 195))
	stroke(row, C(15, 15, 25), 1.5)
	return row
end

local function rowButton(row)
	local button = makeButton({
		AnchorPoint = Vector2.new(1, 0.5),
		Position = UDim2.new(1, -8, 0.5, 0),
		Size = UDim2.new(0, 160, 1, -14),
		BackgroundColor3 = GRAY,
	}, 5)
	button.Parent = row
	return button
end

local function sectionTitle(parent, order, text)
	label({
		LayoutOrder = order,
		Size = UDim2.new(1, -10, 0, 26),
		Text = text,
		TextXAlignment = Enum.TextXAlignment.Left,
		TextColor3 = GOLD,
		Font = Enum.Font.GothamBlack,
		Parent = parent,
	})
end

local function listLayout(parent, padding)
	create("UIListLayout", { Padding = UDim.new(0, padding or 8), SortOrder = Enum.SortOrder.LayoutOrder, Parent = parent })
end

------------------------------------------------------------------
-- ИНВЕНТАРЬ ПИТОМЦЕВ
------------------------------------------------------------------
local inventory = { pets = {}, equipped = {} }
local equippedSet = {}

local function sortedPets()
	local list = table.clone(inventory.pets)
	table.sort(list, function(a, b)
		local ea, eb = equippedSet[a.id] == true, equippedSet[b.id] == true
		if ea ~= eb then
			return ea
		end
		local ba, bb = INFO.pets[a.kind].bonus, INFO.pets[b.kind].bonus
		if ba ~= bb then
			return ba > bb
		end
		return a.id < b.id
	end)
	return list
end

------------------------------------------------------------------
-- ОКНО: КИРКИ И АДДОНЫ
------------------------------------------------------------------
local pickWindow = makeWindow("pickaxes", "⛏ Кирки и аддоны", C(200, 120, 40))
local pickList = scrolling({ Size = UDim2.fromScale(1, 1), Parent = pickWindow.body })
listLayout(pickList, 8)

pickWindow.render = function()
	clear(pickList)
	local coins = coinsValue.Value
	local current = player:GetAttribute("Pickaxe") or 1
	local order = 0
	local function nextOrder()
		order += 1
		return order
	end

	sectionTitle(pickList, nextOrder(), "Кирки: урон умножается на силу кирки")
	for tier, pick in INFO.pickaxes do
		local row = makeRow(pickList, nextOrder(), 58)
		local swatch = create("Frame", { Position = UDim2.fromOffset(10, 11), Size = UDim2.fromOffset(36, 36), BackgroundColor3 = pick.color, Parent = row })
		round(swatch, 6)
		label({
			Position = UDim2.fromOffset(56, 5),
			Size = UDim2.new(1, -236, 0, 26),
			Text = pick.name,
			TextXAlignment = Enum.TextXAlignment.Left,
			TextColor3 = pick.color,
			Parent = row,
		})
		label({
			Position = UDim2.fromOffset(56, 32),
			Size = UDim2.new(1, -236, 0, 20),
			Text = "Урон x" .. abbreviate(pick.damage) .. " · аддоны до ур. " .. pick.addonCap,
			TextXAlignment = Enum.TextXAlignment.Left,
			TextColor3 = SOFT,
			Font = Enum.Font.Gotham,
			Parent = row,
		})
		local button = rowButton(row)
		if tier < current then
			button.Text = "✔ Есть"
		elseif tier == current then
			button.Text = "⛏ В руках"
			button.BackgroundColor3 = BLUE
		elseif tier == current + 1 then
			button.Text = "Купить\n💰 " .. abbreviate(pick.price)
			button.BackgroundColor3 = coins >= pick.price and GREEN or GRAY
			button.Activated:Connect(function()
				shopRemote:FireServer("pickaxe")
			end)
		else
			button.Text = "🔒 Сначала прошлую"
		end
	end

	local cap = INFO.pickaxes[current].addonCap
	sectionTitle(pickList, nextOrder(), "Аддоны: до ур. " .. cap .. " с твоей киркой")
	for _, addon in INFO.addons do
		local level = player:GetAttribute("Addon_" .. addon.id) or 0
		local row = makeRow(pickList, nextOrder(), 58)
		label({
			Position = UDim2.fromOffset(12, 5),
			Size = UDim2.new(1, -190, 0, 26),
			Text = addon.name .. "  (ур. " .. level .. "/" .. cap .. ")",
			TextXAlignment = Enum.TextXAlignment.Left,
			Parent = row,
		})
		label({
			Position = UDim2.fromOffset(12, 32),
			Size = UDim2.new(1, -190, 0, 20),
			Text = addon.desc,
			TextXAlignment = Enum.TextXAlignment.Left,
			TextColor3 = SOFT,
			Font = Enum.Font.Gotham,
			Parent = row,
		})
		local button = rowButton(row)
		if level >= cap then
			button.Text = cap >= INFO.pickaxes[#INFO.pickaxes].addonCap and "✨ MAX" or "Нужна кирка\nполучше"
		else
			local cost = math.floor(addon.baseCost * addon.growth ^ level)
			button.Text = "Улучшить\n💰 " .. abbreviate(cost)
			button.BackgroundColor3 = coins >= cost and GREEN or GRAY
			button.Activated:Connect(function()
				shopRemote:FireServer("addon", addon.id)
			end)
		end
	end
end

------------------------------------------------------------------
-- ОКНО: ПИТОМЦЫ
------------------------------------------------------------------
local petsWindow = makeWindow("pets", "🐾 Питомцы", C(220, 90, 150))
local petsInfo = label({
	Size = UDim2.new(1, -190, 0, 30),
	TextXAlignment = Enum.TextXAlignment.Left,
	Font = Enum.Font.Gotham,
	Parent = petsWindow.body,
})
local bestButton = makeButton({
	AnchorPoint = Vector2.new(1, 0),
	Position = UDim2.new(1, 0, 0, 0),
	Size = UDim2.fromOffset(180, 32),
	BackgroundColor3 = C(210, 160, 30),
	Text = "⭐ Надеть лучших",
}, 5)
bestButton.Parent = petsWindow.body
bestButton.Activated:Connect(function()
	petRemote:FireServer("best")
end)
local petsGrid = scrolling({ Position = UDim2.fromOffset(0, 40), Size = UDim2.new(1, 0, 1, -40), Parent = petsWindow.body })
create("UIGridLayout", {
	CellSize = UDim2.fromOffset(104, 128),
	CellPadding = UDim2.fromOffset(8, 8),
	SortOrder = Enum.SortOrder.LayoutOrder,
	Parent = petsGrid,
})

petsWindow.render = function()
	clear(petsGrid)
	petsInfo.Text = string.format(
		"Питомцы: %d/%d   Надето: %d/%d   Бонус: +%s",
		#inventory.pets,
		INFO.maxPets,
		#inventory.equipped,
		INFO.maxEquipped,
		percent(player:GetAttribute("PetBonus") or 0)
	)
	if #inventory.pets == 0 then
		label({
			Size = UDim2.fromOffset(500, 30),
			Text = "Пока пусто. Открой яйцо в меню 🥚 Яйца!",
			Font = Enum.Font.Gotham,
			Parent = petsGrid,
		})
		return
	end
	for index, pet in sortedPets() do
		local equipped = equippedSet[pet.id] == true
		local card = petCard(pet.kind, petsGrid, index, equipped)
		card.Activated:Connect(function()
			petRemote:FireServer(equipped and "unequip" or "equip", pet.id)
		end)
		local trash = makeButton({
			AnchorPoint = Vector2.new(1, 0),
			Position = UDim2.new(1, -4, 0, 4),
			Size = UDim2.fromOffset(26, 26),
			BackgroundColor3 = C(70, 70, 85),
			Text = "🗑",
			ZIndex = 3,
		}, 3)
		trash.Parent = card
		local armed = false
		trash.Activated:Connect(function()
			if armed then
				petRemote:FireServer("delete", pet.id)
				return
			end
			armed = true
			trash.BackgroundColor3 = RED
			trash.Text = "?"
			task.delay(2, function()
				if trash.Parent then
					armed = false
					trash.BackgroundColor3 = C(70, 70, 85)
					trash.Text = "🗑"
				end
			end)
		end)
	end
end

------------------------------------------------------------------
-- ОКНО: ЯЙЦА
------------------------------------------------------------------
local eggsWindow = makeWindow("eggs", "🥚 Яйца", C(230, 180, 40))
local eggsList = scrolling({ Size = UDim2.fromScale(1, 1), Parent = eggsWindow.body })
listLayout(eggsList, 10)
local eggButtons = {}

local function updateEggButtons()
	local unlocked = unlockedWorlds()
	for id, entry in eggButtons do
		local world = entry.world
		if not unlocked[id] then
			entry.button.Text = "🔒 Открой мир\n" .. world.name
			entry.button.BackgroundColor3 = GRAY
		else
			entry.button.Text = "Открыть\n💰 " .. abbreviate(world.egg.price)
			entry.button.BackgroundColor3 = coinsValue.Value >= world.egg.price and GREEN or GRAY
		end
	end
end

eggsWindow.render = function()
	clear(eggsList)
	eggButtons = {}
	for _, world in INFO.worlds do
		local row = makeRow(eggsList, world.id, 150)
		label({
			Position = UDim2.fromOffset(12, 6),
			Size = UDim2.new(1, -200, 0, 28),
			Text = "🥚 " .. world.egg.name .. " · мир «" .. world.name .. "»",
			TextXAlignment = Enum.TextXAlignment.Left,
			TextColor3 = world.accent,
			Parent = row,
		})
		local strip = create("Frame", {
			Position = UDim2.fromOffset(12, 40),
			Size = UDim2.new(1, -200, 0, 102),
			BackgroundTransparency = 1,
			Parent = row,
		})
		create("UIListLayout", {
			FillDirection = Enum.FillDirection.Horizontal,
			Padding = UDim.new(0, 6),
			SortOrder = Enum.SortOrder.LayoutOrder,
			Parent = strip,
		})
		for index, entry in world.egg.pets do
			local rarity = INFO.rarities[INFO.pets[entry.kind].rarity]
			local mini = create("Frame", {
				LayoutOrder = index,
				Size = UDim2.fromOffset(72, 102),
				BackgroundColor3 = C(35, 35, 48),
				Parent = strip,
			})
			round(mini, 8)
			stroke(mini, rarity.color, 1.5)
			petViewport(entry.kind, mini, { Size = UDim2.new(1, 0, 0, 58) })
			label({ Position = UDim2.fromOffset(3, 58), Size = UDim2.new(1, -6, 0, 20), Text = entry.kind, TextColor3 = rarity.color, Parent = mini })
			local chance = entry.chance < 1 and string.format("%.1f%%", entry.chance) or (math.floor(entry.chance + 0.5) .. "%")
			label({ Position = UDim2.fromOffset(3, 78), Size = UDim2.new(1, -6, 0, 20), Text = chance, Parent = mini })
		end
		local button = rowButton(row)
		button.Size = UDim2.fromOffset(170, 64)
		button.Activated:Connect(function()
			openEggRemote:FireServer(world.id)
		end)
		eggButtons[world.id] = { button = button, world = world }
	end
	updateEggButtons()
end

-- Анимация открытия яйца
local eggOverlay = create("TextButton", {
	Size = UDim2.fromScale(1, 1),
	BackgroundColor3 = Color3.new(0, 0, 0),
	BackgroundTransparency = 0.35,
	AutoButtonColor = false,
	Text = "",
	Visible = false,
	ZIndex = 20,
	Parent = gui,
})
local eggIcon = label({
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.45),
	Size = UDim2.fromOffset(200, 200),
	Text = "🥚",
	Parent = eggOverlay,
})
local eggResult = create("Frame", {
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.45),
	Size = UDim2.fromOffset(300, 320),
	BackgroundTransparency = 1,
	Visible = false,
	Parent = eggOverlay,
})
local eggResultScale = create("UIScale", { Parent = eggResult })
local eggResultView = create("Frame", { Size = UDim2.fromOffset(300, 220), BackgroundTransparency = 1, Parent = eggResult })
local eggResultName = label({
	Position = UDim2.fromOffset(0, 220),
	Size = UDim2.fromOffset(300, 46),
	Font = Enum.Font.GothamBlack,
	Parent = eggResult,
})
local eggResultInfo = label({ Position = UDim2.fromOffset(0, 270), Size = UDim2.fromOffset(300, 30), Parent = eggResult })
local eggToken = 0

eggOverlay.Activated:Connect(function()
	eggToken += 1
	eggOverlay.Visible = false
end)

eggResultRemote.OnClientEvent:Connect(function(kind)
	eggToken += 1
	local token = eggToken
	eggOverlay.Visible = true
	eggResult.Visible = false
	eggIcon.Visible = true
	eggIcon.Rotation = 0
	eggIcon.Size = UDim2.fromOffset(200, 200)
	for _ = 1, 3 do
		TweenService:Create(eggIcon, TweenInfo.new(0.1), { Rotation = -18 }):Play()
		task.wait(0.12)
		TweenService:Create(eggIcon, TweenInfo.new(0.1), { Rotation = 18 }):Play()
		task.wait(0.12)
		if token ~= eggToken then
			return
		end
	end
	TweenService:Create(eggIcon, TweenInfo.new(0.2), { Rotation = 0, Size = UDim2.fromOffset(300, 300) }):Play()
	task.wait(0.25)
	if token ~= eggToken then
		return
	end
	eggIcon.Visible = false
	clear(eggResultView)
	petViewport(kind, eggResultView, {})
	local info = INFO.pets[kind]
	local rarity = INFO.rarities[info.rarity]
	eggResultName.Text = kind
	eggResultName.TextColor3 = rarity.color
	eggResultInfo.Text = info.rarity .. " · +" .. percent(info.bonus) .. " монет"
	eggResult.Visible = true
	playSound("reveal")
	eggResultScale.Scale = 0.4
	TweenService:Create(eggResultScale, TweenInfo.new(0.35, Enum.EasingStyle.Back, Enum.EasingDirection.Out), { Scale = 1 }):Play()
	task.wait(2.5)
	if token == eggToken then
		eggOverlay.Visible = false
	end
end)

------------------------------------------------------------------
-- ОКНО: МИРЫ
------------------------------------------------------------------
local worldsWindow = makeWindow("worlds", "🌍 Миры", BLUE)
local worldsList = scrolling({ Size = UDim2.fromScale(1, 1), Parent = worldsWindow.body })
listLayout(worldsList, 10)

worldsWindow.render = function()
	clear(worldsList)
	local unlocked = unlockedWorlds()
	local current = player:GetAttribute("World") or 1
	for _, world in INFO.worlds do
		local row = makeRow(worldsList, world.id, 72)
		label({
			Position = UDim2.fromOffset(14, 8),
			Size = UDim2.new(1, -200, 0, 30),
			Text = "🌍 " .. world.name,
			TextXAlignment = Enum.TextXAlignment.Left,
			TextColor3 = world.accent,
			Font = Enum.Font.GothamBlack,
			Parent = row,
		})
		label({
			Position = UDim2.fromOffset(14, 42),
			Size = UDim2.new(1, -200, 0, 20),
			Text = unlocked[world.id] and ("✔ Открыт · яйцо: " .. world.egg.name) or ("Цена: 💰 " .. abbreviate(world.price)),
			TextXAlignment = Enum.TextXAlignment.Left,
			TextColor3 = SOFT,
			Font = Enum.Font.Gotham,
			Parent = row,
		})
		local button = rowButton(row)
		if world.id == current then
			button.Text = "📍 Ты здесь"
		elseif unlocked[world.id] then
			button.Text = "Телепорт"
			button.BackgroundColor3 = BLUE
		else
			button.Text = "Открыть\n💰 " .. abbreviate(world.price)
			button.BackgroundColor3 = coinsValue.Value >= world.price and GREEN or GRAY
		end
		if world.id ~= current then
			button.Activated:Connect(function()
				worldRemote:FireServer(world.id)
			end)
		end
	end
end

------------------------------------------------------------------
-- ОКНО: ТРЕЙД
------------------------------------------------------------------
local tradeWindow = makeWindow("trade", "🤝 Трейд", PURPLE)
local tradeState = nil
local countdownStartedAt = 0

local tradeLobby = create("Frame", { Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1, Parent = tradeWindow.body })
label({
	Size = UDim2.new(1, 0, 0, 26),
	Text = "Выбери игрока, с которым хочешь обменяться питомцами:",
	TextXAlignment = Enum.TextXAlignment.Left,
	Font = Enum.Font.Gotham,
	Parent = tradeLobby,
})
local tradePlayers = scrolling({ Position = UDim2.fromOffset(0, 34), Size = UDim2.new(1, 0, 1, -34), Parent = tradeLobby })
listLayout(tradePlayers, 8)

local tradeActive = create("Frame", { Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1, Visible = false, Parent = tradeWindow.body })

local function offerColumn(x, title)
	local column = create("Frame", {
		Position = UDim2.new(x, x > 0 and 5 or 0, 0, 0),
		Size = UDim2.new(0.5, -5, 0, 150),
		BackgroundColor3 = CARD,
		Parent = tradeActive,
	})
	round(column, 10)
	local titleLabel = label({
		Position = UDim2.fromOffset(8, 4),
		Size = UDim2.new(1, -16, 0, 22),
		Text = title,
		TextXAlignment = Enum.TextXAlignment.Left,
		Parent = column,
	})
	local grid = scrolling({ Position = UDim2.fromOffset(6, 30), Size = UDim2.new(1, -12, 1, -36), Parent = column })
	create("UIGridLayout", {
		CellSize = UDim2.fromOffset(64, 80),
		CellPadding = UDim2.fromOffset(6, 6),
		SortOrder = Enum.SortOrder.LayoutOrder,
		Parent = grid,
	})
	return titleLabel, grid
end
local _, mineGrid = offerColumn(0, "Ты отдаёшь (нажми, чтобы убрать)")
local theirTitle, theirGrid = offerColumn(0.5, "Партнёр отдаёт")

local tradeStatus = label({ Position = UDim2.fromOffset(0, 156), Size = UDim2.new(1, 0, 0, 24), Parent = tradeActive })
label({
	Position = UDim2.fromOffset(0, 184),
	Size = UDim2.new(1, 0, 0, 20),
	Text = "Твои питомцы: нажми, чтобы добавить в трейд",
	TextXAlignment = Enum.TextXAlignment.Left,
	Font = Enum.Font.Gotham,
	TextColor3 = SOFT,
	Parent = tradeActive,
})
local tradeInventory = scrolling({ Position = UDim2.fromOffset(0, 206), Size = UDim2.new(1, 0, 0, 92), Parent = tradeActive })
create("UIGridLayout", {
	CellSize = UDim2.fromOffset(64, 80),
	CellPadding = UDim2.fromOffset(6, 6),
	SortOrder = Enum.SortOrder.LayoutOrder,
	Parent = tradeInventory,
})
local readyButton = makeButton({ Position = UDim2.fromOffset(0, 306), Size = UDim2.new(0.5, -5, 0, 44) }, 8)
readyButton.Parent = tradeActive
local cancelButton = makeButton({
	Position = UDim2.new(0.5, 5, 0, 306),
	Size = UDim2.new(0.5, -5, 0, 44),
	BackgroundColor3 = RED,
	Text = "❌ Отменить",
}, 8)
cancelButton.Parent = tradeActive

readyButton.Activated:Connect(function()
	tradeRemote:FireServer("ready")
end)
cancelButton.Activated:Connect(function()
	tradeRemote:FireServer("cancel")
end)

local function updateTradeStatus()
	if not tradeState then
		return
	end
	if tradeState.countdown then
		local left = math.max(0, math.ceil(INFO.tradeCountdown - (os.clock() - countdownStartedAt)))
		tradeStatus.Text = "🔄 Обмен через " .. left .. " сек…"
		tradeStatus.TextColor3 = GOLD
	else
		local me = tradeState.myReady and "✅ Ты готов" or "⏳ Ты не готов"
		local them = tradeState.theirReady and ("✅ " .. tradeState.partner .. " готов") or ("⏳ " .. tradeState.partner .. " не готов")
		tradeStatus.Text = me .. "   ·   " .. them
		tradeStatus.TextColor3 = WHITE
	end
end

tradeWindow.render = function()
	if tradeState then
		tradeLobby.Visible = false
		tradeActive.Visible = true
		tradeWindow.title.Text = "🤝 Трейд с " .. tradeState.partner
		theirTitle.Text = tradeState.partner .. " отдаёт"
		clear(mineGrid)
		clear(theirGrid)
		clear(tradeInventory)
		local offered = {}
		for index, pet in tradeState.mine do
			offered[pet.id] = true
			local card = petCard(pet.kind, mineGrid, index, false)
			card.Activated:Connect(function()
				tradeRemote:FireServer("remove", pet.id)
			end)
		end
		for index, pet in tradeState.theirs do
			petCard(pet.kind, theirGrid, index, false)
		end
		for index, pet in sortedPets() do
			if not offered[pet.id] then
				local card = petCard(pet.kind, tradeInventory, index, equippedSet[pet.id] == true)
				card.Activated:Connect(function()
					tradeRemote:FireServer("add", pet.id)
				end)
			end
		end
		readyButton.Text = tradeState.myReady and "↩ Не готов" or "✅ Готов"
		readyButton.BackgroundColor3 = tradeState.myReady and GRAY or GREEN
		updateTradeStatus()
	else
		tradeLobby.Visible = true
		tradeActive.Visible = false
		tradeWindow.title.Text = "🤝 Трейд"
		clear(tradePlayers)
		local count = 0
		for _, other in Players:GetPlayers() do
			if other ~= player then
				count += 1
				local row = makeRow(tradePlayers, count, 56)
				label({
					Position = UDim2.fromOffset(14, 0),
					Size = UDim2.new(1, -200, 1, 0),
					Text = other.DisplayName,
					TextXAlignment = Enum.TextXAlignment.Left,
					Parent = row,
				})
				local button = rowButton(row)
				button.Text = "Предложить трейд"
				button.BackgroundColor3 = PURPLE
				button.Activated:Connect(function()
					tradeRemote:FireServer("request", other.UserId)
				end)
			end
		end
		if count == 0 then
			label({
				Size = UDim2.new(1, -10, 0, 30),
				Text = "На сервере пока нет других игроков. Позови друга! 🙂",
				Font = Enum.Font.Gotham,
				Parent = tradePlayers,
			})
		end
	end
end

tradeWindow.onClose = function()
	if tradeState then
		tradeRemote:FireServer("cancel")
	end
end

-- Всплывашка «тебе предлагают трейд»
local toast = create("Frame", {
	AnchorPoint = Vector2.new(1, 0.5),
	Position = UDim2.new(1, -12, 0.5, 0),
	Size = UDim2.fromOffset(260, 120),
	BackgroundColor3 = PANEL,
	Visible = false,
	ZIndex = 8,
	Parent = gui,
})
round(toast, 12)
stroke(toast, GOLD, 3)
gradient(toast, WHITE, C(150, 150, 175))
local toastText = label({ Position = UDim2.fromOffset(10, 8), Size = UDim2.new(1, -20, 0, 50), Parent = toast })
local acceptButton = makeButton({ Position = UDim2.fromOffset(10, 66), Size = UDim2.fromOffset(115, 44), Text = "✅ Принять" }, 6)
acceptButton.Parent = toast
local declineButton = makeButton({ Position = UDim2.fromOffset(135, 66), Size = UDim2.fromOffset(115, 44), BackgroundColor3 = RED, Text = "❌ Нет" }, 6)
declineButton.Parent = toast
local pendingRequest = nil
local toastToken = 0

local function answerRequest(action)
	if pendingRequest then
		tradeRemote:FireServer(action, pendingRequest.userId)
	end
	pendingRequest = nil
	toast.Visible = false
end
acceptButton.Activated:Connect(function()
	answerRequest("accept")
end)
declineButton.Activated:Connect(function()
	answerRequest("decline")
end)

tradeRemote.OnClientEvent:Connect(function(kind, data)
	if kind == "request" then
		pendingRequest = data
		toastText.Text = "🤝 " .. data.name .. " предлагает трейд"
		toast.Visible = true
		toastToken += 1
		local token = toastToken
		task.delay(20, function()
			if token == toastToken then
				toast.Visible = false
				pendingRequest = nil
			end
		end)
	elseif kind == "state" then
		if data.countdown and not (tradeState and tradeState.countdown) then
			countdownStartedAt = os.clock()
		end
		tradeState = data
		toast.Visible = false
		if openName == "trade" then
			tradeWindow.render()
		else
			openWindow("trade")
		end
	elseif kind == "closed" then
		tradeState = nil
		if openName == "trade" then
			tradeWindow.render()
		end
	end
end)

------------------------------------------------------------------
-- ОБНОВЛЕНИЕ ИНТЕРФЕЙСА
------------------------------------------------------------------
local rebirthConfirmUntil = 0

local function refreshHud()
	local coins = coinsValue.Value
	local level = player:GetAttribute("Level") or 1
	local levelCost = player:GetAttribute("LevelCost")
	local rebirthCost = player:GetAttribute("RebirthCost")

	coinsLabel.Text = "💰 " .. abbreviate(coins)
	statsLabel.Text = string.format(
		"⛏ Урон: %s   🔁 Ребёрты: %d   ✖ Монеты x%s",
		abbreviate(player:GetAttribute("Damage") or 1),
		rebirthsValue.Value,
		formatMultiplier(player:GetAttribute("CoinMultiplier") or 1)
	)

	local canLevel = levelCost ~= nil and coins >= levelCost
	levelButton.Text = "⛏ УРОВЕНЬ " .. level .. " → " .. (level + 1) .. "\n💰 " .. (levelCost and abbreviate(levelCost) or "...")
	levelButton.BackgroundColor3 = canLevel and GREEN or GRAY
	levelMaxButton.Text = "⛏ КУПИТЬ МАКС"
	levelMaxButton.BackgroundColor3 = canLevel and GREEN or GRAY

	local canRebirth = rebirthCost ~= nil and coins >= rebirthCost
	if os.clock() < rebirthConfirmUntil then
		rebirthButton.Text = "❗ ТОЧНО? Уровень и монеты сбросятся"
		rebirthButton.BackgroundColor3 = RED
	else
		rebirthButton.Text = "🔁 РЕБЁРТ: больше монет\n💰 " .. (rebirthCost and abbreviate(rebirthCost) or "...")
		rebirthButton.BackgroundColor3 = canRebirth and PURPLE or GRAY
	end
end

-- Окна перерисовываем не чаще 4 раз в секунду
local renderQueued = false
local function queueRender()
	if renderQueued then
		return
	end
	renderQueued = true
	task.delay(0.25, function()
		renderQueued = false
		if openName == "pickaxes" or openName == "worlds" then
			windows[openName].render()
		elseif openName == "eggs" then
			updateEggButtons()
		elseif openName == "pets" then
			petsWindow.render()
		end
	end)
end

local bannerToken = 0
local function showBanner(text, color)
	bannerToken += 1
	local token = bannerToken
	banner.Text = text
	banner.TextColor3 = color or WHITE
	banner.TextTransparency = 0
	bannerStroke.Transparency = 0
	bannerScale.Scale = 0.6
	TweenService:Create(bannerScale, TweenInfo.new(0.35, Enum.EasingStyle.Back, Enum.EasingDirection.Out), { Scale = 1 }):Play()
	task.delay(3, function()
		if token == bannerToken then
			TweenService:Create(banner, TweenInfo.new(0.5), { TextTransparency = 1 }):Play()
			TweenService:Create(bannerStroke, TweenInfo.new(0.5), { Transparency = 1 }):Play()
		end
	end)
end
announceRemote.OnClientEvent:Connect(showBanner)

local comboToken = 0
local function onCombo()
	local combo = player:GetAttribute("Combo") or 0
	playSound("hit")
	comboToken += 1
	local token = comboToken
	if combo < 3 then
		comboLabel.Visible = false
		return
	end
	comboLabel.Visible = true
	comboLabel.Text = "КОМБО x" .. combo .. "!"
	comboLabel.TextColor3 = Color3.fromHSV(math.max(0, 0.15 - combo / 330), 1, 1)
	comboScale.Scale = 1.3
	TweenService:Create(comboScale, TweenInfo.new(0.15, Enum.EasingStyle.Back, Enum.EasingDirection.Out), { Scale = 1 }):Play()
	task.delay(INFO.comboWindow, function()
		if token == comboToken then
			comboLabel.Visible = false
		end
	end)
end

------------------------------------------------------------------
-- ОСВЕЩЕНИЕ МИРОВ (только у тебя на экране)
------------------------------------------------------------------
local atmosphere = Lighting:FindFirstChildOfClass("Atmosphere") or create("Atmosphere", { Parent = Lighting })
local sky = Lighting:FindFirstChildOfClass("Sky")
local tint = create("ColorCorrectionEffect", { Name = "DestroyTint", Parent = Lighting })
-- Цветокоррекция, свечение неона и солнечные лучи
local grade = create("ColorCorrectionEffect", { Name = "DestroyGrade", Parent = Lighting })
local bloom = Lighting:FindFirstChildOfClass("BloomEffect") or create("BloomEffect", { Parent = Lighting })
bloom.Intensity = 0.8
bloom.Size = 30
bloom.Threshold = 1.3
local sunRays = Lighting:FindFirstChildOfClass("SunRaysEffect") or create("SunRaysEffect", { Parent = Lighting })
sunRays.Intensity = 0.06
sunRays.Spread = 0.6
Lighting.GlobalShadows = true
Lighting.ShadowSoftness = 0.25
Lighting.EnvironmentDiffuseScale = 1
Lighting.EnvironmentSpecularScale = 1

local WORLD_LOOK = {
	[1] = {
		clock = 14,
		brightness = 2.5,
		ambient = C(110, 110, 120),
		outdoor = C(140, 140, 150),
		density = 0.3,
		color = C(199, 220, 255),
		decay = C(110, 150, 200),
		haze = 1,
		stars = 0,
		contrast = 0.12,
		saturation = 0.2,
	},
	[2] = {
		clock = 0,
		brightness = 1,
		ambient = C(150, 70, 50),
		outdoor = C(170, 80, 60),
		density = 0.5,
		color = C(170, 50, 30),
		decay = C(90, 20, 10),
		haze = 2.5,
		stars = 0,
		contrast = 0.15,
		saturation = 0.1,
	},
	[3] = {
		clock = 0,
		brightness = 1,
		ambient = C(110, 90, 140),
		outdoor = C(120, 100, 150),
		density = 0.32,
		color = C(70, 40, 100),
		decay = C(25, 10, 45),
		haze = 1.5,
		stars = 3000,
		contrast = 0.15,
		saturation = 0.15,
	},
}

local function applyWorldLook()
	local look = WORLD_LOOK[player:GetAttribute("World") or 1] or WORLD_LOOK[1]
	Lighting.ClockTime = look.clock
	TweenService:Create(Lighting, TweenInfo.new(1), {
		Brightness = look.brightness,
		Ambient = look.ambient,
		OutdoorAmbient = look.outdoor,
	}):Play()
	TweenService:Create(atmosphere, TweenInfo.new(1), {
		Density = look.density,
		Color = look.color,
		Decay = look.decay,
		Haze = look.haze,
	}):Play()
	TweenService:Create(grade, TweenInfo.new(1), {
		Contrast = look.contrast,
		Saturation = look.saturation,
	}):Play()
	if sky then
		sky.StarCount = look.stars
	end
end

------------------------------------------------------------------
-- ПИТОМЦЫ ХОДЯТ ЗА ИГРОКАМИ
------------------------------------------------------------------
local petsFolder = create("Folder", { Name = "ClientPets", Parent = workspace })
local followers = {} -- [Player] = { key, models, lifts }
local FOLLOW_OFFSETS = { V(-3.5, 0, 3), V(3.5, 0, 3), V(0, 0, 5.5) }

local function clearFollower(plr)
	local follower = followers[plr]
	if follower then
		for _, model in follower.models do
			model:Destroy()
		end
		followers[plr] = nil
	end
end

local function syncFollower(plr)
	local key = plr:GetAttribute("EquippedPets") or ""
	local follower = followers[plr]
	if follower and follower.key == key then
		return
	end
	clearFollower(plr)
	follower = { key = key, models = {}, lifts = {} }
	followers[plr] = follower
	for kind in string.gmatch(key, "[^,]+") do
		local template = petModels:FindFirstChild(kind)
		if template then
			local model = template:Clone()
			local box, size = model:GetBoundingBox()
			-- на сколько поднять центр питомца, чтобы он стоял на земле
			table.insert(follower.lifts, model:GetPivot().Position.Y - (box.Position.Y - size.Y / 2))
			model.Parent = petsFolder
			table.insert(follower.models, model)
		end
	end
end

local function watchPlayer(plr)
	plr:GetAttributeChangedSignal("EquippedPets"):Connect(function()
		syncFollower(plr)
	end)
	syncFollower(plr)
end
Players.PlayerAdded:Connect(watchPlayer)
for _, plr in Players:GetPlayers() do
	watchPlayer(plr)
end
Players.PlayerRemoving:Connect(clearFollower)

RunService.RenderStepped:Connect(function(dt)
	local t = os.clock()
	for plr, follower in followers do
		local character = plr.Character
		local root = character and character:FindFirstChild("HumanoidRootPart")
		local humanoid = character and character:FindFirstChildOfClass("Humanoid")
		if root and root:IsA("BasePart") and humanoid then
			local groundY = humanoid.RigType == Enum.HumanoidRigType.R6 and root.Position.Y - 3
				or root.Position.Y - humanoid.HipHeight - root.Size.Y / 2
			local moving = humanoid.MoveDirection.Magnitude > 0.1
			for i, model in follower.models do
				local info = INFO.pets[model.Name]
				local offset = FOLLOW_OFFSETS[(i - 1) % #FOLLOW_OFFSETS + 1]
				local flat = (root.CFrame * CFrame.new(offset)).Position
				local bob
				if info and info.fly then
					bob = 3 + math.sin(t * 2 + i) * 0.4
				else
					bob = math.abs(math.sin(t * 10 + i)) * (moving and 0.6 or 0.1)
				end
				local target = CFrame.new(flat.X, groundY + follower.lifts[i] + bob, flat.Z) * root.CFrame.Rotation
				local current = model:GetPivot()
				if (current.Position - target.Position).Magnitude > 60 then
					model:PivotTo(target)
				else
					model:PivotTo(current:Lerp(target, math.min(1, dt * 10)))
				end
			end
		end
	end
end)

------------------------------------------------------------------
-- КИРКА: удар по блоку, на который смотрит мышка/палец
------------------------------------------------------------------
local mouse = player:GetMouse()
local hookedTools = setmetatable({}, { __mode = "k" })

local function hookTool(tool)
	if hookedTools[tool] or not tool:IsA("Tool") or not tool:GetAttribute("Pickaxe") then
		return
	end
	hookedTools[tool] = true
	tool.Activated:Connect(function()
		local target = mouse.Target
		local model = target and target:FindFirstAncestorOfClass("Model")
		if model and model.Parent == blocksFolder then
			hitRemote:FireServer(model)
		end
	end)
end

local function onCharacter(character)
	character.ChildAdded:Connect(hookTool)
	for _, child in character:GetChildren() do
		hookTool(child)
	end
end
player.CharacterAdded:Connect(onCharacter)
if player.Character then
	onCharacter(player.Character)
end

------------------------------------------------------------------
-- СОБЫТИЯ
------------------------------------------------------------------
local lastCoins = coinsValue.Value
coinsValue.Changed:Connect(function(value)
	if value > lastCoins then
		playSound("coin")
		coinsScale.Scale = 1.15
		TweenService:Create(coinsScale, TweenInfo.new(0.25, Enum.EasingStyle.Back, Enum.EasingDirection.Out), { Scale = 1 }):Play()
	end
	lastCoins = value
	refreshHud()
	queueRender()
end)
rebirthsValue.Changed:Connect(refreshHud)

player.AttributeChanged:Connect(function(name)
	if name == "Combo" then
		onCombo()
		return
	end
	if name == "World" then
		applyWorldLook()
	end
	refreshHud()
	queueRender()
end)

inventoryRemote.OnClientEvent:Connect(function(data)
	inventory = data
	equippedSet = {}
	for _, id in inventory.equipped do
		equippedSet[id] = true
	end
	if openName == "pets" then
		petsWindow.render()
	elseif openName == "trade" and tradeState then
		tradeWindow.render()
	end
end)

pickaxesMenu.Activated:Connect(function()
	toggleWindow("pickaxes")
end)
petsMenu.Activated:Connect(function()
	toggleWindow("pets")
end)
eggsMenu.Activated:Connect(function()
	toggleWindow("eggs")
end)
worldsMenu.Activated:Connect(function()
	toggleWindow("worlds")
end)
tradeMenu.Activated:Connect(function()
	toggleWindow("trade")
end)

levelButton.Activated:Connect(function()
	shopRemote:FireServer("level")
end)
levelMaxButton.Activated:Connect(function()
	shopRemote:FireServer("levelMax")
end)
rebirthButton.Activated:Connect(function()
	local cost = player:GetAttribute("RebirthCost")
	if not cost or coinsValue.Value < cost then
		showBanner("Для ребёрта нужно 💰 " .. (cost and abbreviate(cost) or "..."), RED)
		return
	end
	if os.clock() < rebirthConfirmUntil then
		rebirthConfirmUntil = 0
		shopRemote:FireServer("rebirth")
	else
		rebirthConfirmUntil = os.clock() + 3
		task.delay(3.05, refreshHud)
	end
	refreshHud()
end)

Players.PlayerAdded:Connect(function()
	if openName == "trade" and not tradeState then
		tradeWindow.render()
	end
end)
Players.PlayerRemoving:Connect(function()
	if openName == "trade" and not tradeState then
		task.defer(tradeWindow.render)
	end
end)

-- Таймер «Золотой лихорадки» и отсчёт трейда
task.spawn(function()
	local wasActive = false
	while true do
		local now = workspace:GetServerTimeNow()
		local active = workspace:GetAttribute("EventActive") == true
		if active then
			local left = math.max(0, math.ceil((workspace:GetAttribute("EventEndsAt") or now) - now))
			eventLabel.Text = "🔥 ЗОЛОТАЯ ЛИХОРАДКА x" .. (workspace:GetAttribute("EventMultiplier") or 2) .. " · " .. left .. " сек 🔥"
			eventLabel.TextColor3 = (math.floor(now * 2) % 2 == 0) and GOLD or C(255, 120, 40)
		else
			local left = math.max(0, math.ceil((workspace:GetAttribute("NextEventAt") or now) - now))
			eventLabel.Text = string.format("⏳ Лихорадка через %d:%02d", left // 60, left % 60)
			eventLabel.TextColor3 = WHITE
		end
		if active ~= wasActive then
			wasActive = active
			TweenService:Create(tint, TweenInfo.new(1), {
				TintColor = active and C(255, 225, 160) or WHITE,
				Saturation = active and 0.35 or 0,
			}):Play()
		end
		updateTradeStatus()
		task.wait(0.25)
	end
end)

applyWorldLook()
refreshHud()
petRemote:FireServer("sync")
