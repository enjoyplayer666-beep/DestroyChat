--[[
	DESTROY SIMULATOR — клиентский скрипт (интерфейс игрока)
	Куда положить: StarterPlayer → StarterPlayerScripts → LocalScript (назови GameClient)

	Рисует монеты, кнопки магазина, комбо, таймер лихорадки и объявления.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local player = Players.LocalPlayer
local remotes = ReplicatedStorage:WaitForChild("DestroyRemotes")
local buyUpgradeRemote = remotes:WaitForChild("BuyUpgrade")
local rebirthRemote = remotes:WaitForChild("Rebirth")
local announceRemote = remotes:WaitForChild("Announce")

local leaderstats = player:WaitForChild("leaderstats")
local coinsValue = leaderstats:WaitForChild("Монеты")
local rebirthsValue = leaderstats:WaitForChild("Ребёрты")

local COMBO_WINDOW = 1.5 -- должно совпадать с ComboWindow на сервере

local GREEN = Color3.fromRGB(60, 200, 90)
local PURPLE = Color3.fromRGB(150, 80, 230)
local GRAY = Color3.fromRGB(90, 90, 100)
local GOLD = Color3.fromRGB(255, 200, 40)
local RED = Color3.fromRGB(255, 80, 80)
local WHITE = Color3.new(1, 1, 1)

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

local function round(parent, radius)
	create("UICorner", { CornerRadius = UDim.new(0, radius), Parent = parent })
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

------------------------------------------------------------------
-- ИНТЕРФЕЙС
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
	BackgroundColor3 = Color3.fromRGB(25, 25, 35),
	BackgroundTransparency = 0.15,
	Parent = gui,
})
round(coinsPanel, 14)
pad(coinsPanel, 6)
create("UIStroke", { Color = GOLD, Thickness = 2, Parent = coinsPanel })
local coinsScale = create("UIScale", { Parent = coinsPanel })
local coinsLabel = create("TextLabel", {
	BackgroundTransparency = 1,
	Size = UDim2.fromScale(1, 1),
	Font = Enum.Font.GothamBlack,
	TextScaled = true,
	TextColor3 = GOLD,
	Text = "💰 0",
	Parent = coinsPanel,
})

local statsLabel = create("TextLabel", {
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 70),
	Size = UDim2.fromOffset(440, 24),
	BackgroundTransparency = 1,
	Font = Enum.Font.GothamBold,
	TextScaled = true,
	TextColor3 = WHITE,
	TextStrokeTransparency = 0.4,
	Parent = gui,
})

local eventLabel = create("TextLabel", {
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 98),
	Size = UDim2.fromOffset(420, 30),
	BackgroundTransparency = 1,
	Font = Enum.Font.GothamBlack,
	TextScaled = true,
	TextColor3 = WHITE,
	TextStrokeTransparency = 0.2,
	Parent = gui,
})

-- Кнопки магазина слева
local buttons = create("Frame", {
	AnchorPoint = Vector2.new(0, 0.5),
	Position = UDim2.new(0, 12, 0.5, 0),
	Size = UDim2.fromOffset(220, 236),
	BackgroundTransparency = 1,
	Parent = gui,
})
create("UIListLayout", {
	Padding = UDim.new(0, 10),
	SortOrder = Enum.SortOrder.LayoutOrder,
	Parent = buttons,
})

local function makeButton(order)
	local button = create("TextButton", {
		LayoutOrder = order,
		Size = UDim2.new(1, 0, 0, 72),
		BackgroundColor3 = GRAY,
		Font = Enum.Font.GothamBlack,
		TextScaled = true,
		TextColor3 = WHITE,
		TextStrokeTransparency = 0.5,
		Text = "",
		Parent = buttons,
	})
	round(button, 12)
	pad(button, 8)
	create("UIStroke", {
		Color = Color3.new(0, 0, 0),
		Thickness = 2,
		Transparency = 0.5,
		ApplyStrokeMode = Enum.ApplyStrokeMode.Border,
		Parent = button,
	})
	return button
end

local upgradeButton = makeButton(1)
local maxButton = makeButton(2)
local rebirthButton = makeButton(3)

-- Комбо
local comboLabel = create("TextLabel", {
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.75),
	Size = UDim2.fromOffset(320, 60),
	BackgroundTransparency = 1,
	Font = Enum.Font.GothamBlack,
	TextScaled = true,
	TextStrokeTransparency = 0,
	Visible = false,
	Parent = gui,
})
local comboScale = create("UIScale", { Parent = comboLabel })

-- Большое объявление
local banner = create("TextLabel", {
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.3),
	Size = UDim2.new(0.8, 0, 0, 56),
	BackgroundTransparency = 1,
	Font = Enum.Font.GothamBlack,
	TextScaled = true,
	TextTransparency = 1,
	TextStrokeTransparency = 1,
	Parent = gui,
})
local bannerScale = create("UIScale", { Parent = banner })

------------------------------------------------------------------
-- ЛОГИКА
------------------------------------------------------------------
local rebirthConfirmUntil = 0

local function refresh()
	local coins = coinsValue.Value
	local power = player:GetAttribute("Power") or 1
	local multiplier = player:GetAttribute("CoinMultiplier") or 1
	local upgradeCost = player:GetAttribute("UpgradeCost")
	local rebirthCost = player:GetAttribute("RebirthCost")

	coinsLabel.Text = "💰 " .. abbreviate(coins)
	statsLabel.Text = string.format(
		"⚡ Сила: %s   🔁 Ребёрты: %d   ✖ Монеты x%s",
		abbreviate(power),
		rebirthsValue.Value,
		tostring(multiplier)
	)

	local canUpgrade = upgradeCost ~= nil and coins >= upgradeCost
	upgradeButton.Text = "⚡ СИЛА +1\n💰 " .. (upgradeCost and abbreviate(upgradeCost) or "...")
	upgradeButton.BackgroundColor3 = canUpgrade and GREEN or GRAY
	maxButton.Text = "⚡ КУПИТЬ МАКС"
	maxButton.BackgroundColor3 = canUpgrade and GREEN or GRAY

	local canRebirth = rebirthCost ~= nil and coins >= rebirthCost
	if os.clock() < rebirthConfirmUntil then
		rebirthButton.Text = "❗ ТОЧНО? Сила и монеты сбросятся"
		rebirthButton.BackgroundColor3 = RED
	else
		rebirthButton.Text = "🔁 РЕБЁРТ: больше монет\n💰 " .. (rebirthCost and abbreviate(rebirthCost) or "...")
		rebirthButton.BackgroundColor3 = canRebirth and PURPLE or GRAY
	end
end

local bannerToken = 0
local function showBanner(text, color)
	bannerToken += 1
	local token = bannerToken
	banner.Text = text
	banner.TextColor3 = color or WHITE
	banner.TextTransparency = 0
	banner.TextStrokeTransparency = 0
	bannerScale.Scale = 0.6
	TweenService:Create(bannerScale, TweenInfo.new(0.35, Enum.EasingStyle.Back, Enum.EasingDirection.Out), {
		Scale = 1,
	}):Play()
	task.delay(3, function()
		if token == bannerToken then
			TweenService:Create(banner, TweenInfo.new(0.5), {
				TextTransparency = 1,
				TextStrokeTransparency = 1,
			}):Play()
		end
	end)
end

local comboToken = 0
local function onCombo()
	local combo = player:GetAttribute("Combo") or 0
	comboToken += 1
	local token = comboToken
	if combo < 3 then
		comboLabel.Visible = false
		return
	end
	comboLabel.Visible = true
	comboLabel.Text = "КОМБО x" .. combo .. "!"
	-- от жёлтого к красному по мере роста комбо
	comboLabel.TextColor3 = Color3.fromHSV(math.max(0, 0.15 - combo / 330), 1, 1)
	comboScale.Scale = 1.3
	TweenService:Create(comboScale, TweenInfo.new(0.15, Enum.EasingStyle.Back, Enum.EasingDirection.Out), {
		Scale = 1,
	}):Play()
	task.delay(COMBO_WINDOW, function()
		if token == comboToken then
			comboLabel.Visible = false
		end
	end)
end

local lastCoins = coinsValue.Value
coinsValue.Changed:Connect(function(value)
	if value > lastCoins then
		coinsScale.Scale = 1.15
		TweenService:Create(coinsScale, TweenInfo.new(0.25, Enum.EasingStyle.Back, Enum.EasingDirection.Out), {
			Scale = 1,
		}):Play()
	end
	lastCoins = value
	refresh()
end)
rebirthsValue.Changed:Connect(refresh)

player.AttributeChanged:Connect(function(name)
	if name == "Combo" then
		onCombo()
	else
		refresh()
	end
end)

announceRemote.OnClientEvent:Connect(showBanner)

upgradeButton.Activated:Connect(function()
	buyUpgradeRemote:FireServer("one")
end)

maxButton.Activated:Connect(function()
	buyUpgradeRemote:FireServer("max")
end)

rebirthButton.Activated:Connect(function()
	local cost = player:GetAttribute("RebirthCost")
	if not cost or coinsValue.Value < cost then
		showBanner("Для ребёрта нужно 💰 " .. (cost and abbreviate(cost) or "..."), RED)
		return
	end
	if os.clock() < rebirthConfirmUntil then
		rebirthConfirmUntil = 0
		rebirthRemote:FireServer()
	else
		rebirthConfirmUntil = os.clock() + 3
		task.delay(3.05, refresh)
	end
	refresh()
end)

-- Таймер «Золотой лихорадки»
task.spawn(function()
	while true do
		local now = workspace:GetServerTimeNow()
		if workspace:GetAttribute("EventActive") then
			local left = math.max(0, math.ceil((workspace:GetAttribute("EventEndsAt") or now) - now))
			eventLabel.Text = "🔥 ЗОЛОТАЯ ЛИХОРАДКА x" .. (workspace:GetAttribute("EventMultiplier") or 2) .. " — " .. left .. " сек 🔥"
			eventLabel.TextColor3 = (math.floor(now * 2) % 2 == 0) and GOLD or Color3.fromRGB(255, 120, 40)
		else
			local left = math.max(0, math.ceil((workspace:GetAttribute("NextEventAt") or now) - now))
			eventLabel.Text = string.format("⏳ Лихорадка через %d:%02d", left // 60, left % 60)
			eventLabel.TextColor3 = WHITE
		end
		task.wait(0.5)
	end
end)

refresh()
