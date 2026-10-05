--[[
	DESTROY SIMULATOR — клиентский скрипт (интерфейс игрока)
	Куда положить: StarterPlayer → StarterPlayerScripts → LocalScript

	Рисует интерфейс в стиле симуляторов (жирные обводки, пиксельные панели
	в духе Майнкрафта, 3D-иконки), окна, открытие яиц, питомцев рядом
	с игроками и освещение каждого мира.
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
local adminRemote = remotes:WaitForChild("Admin")
local INFO = remotes:WaitForChild("GetInfo"):InvokeServer()
local petModels = ReplicatedStorage:WaitForChild("PetModels")
local icons = ReplicatedStorage:WaitForChild("Icons")
local blocksFolder = workspace:WaitForChild("Destructibles")

local leaderstats = player:WaitForChild("leaderstats")
local coinsValue = leaderstats:WaitForChild("Монеты")
local rebirthsValue = leaderstats:WaitForChild("Ребёрты")

------------------------------------------------------------------
-- ЦВЕТА
------------------------------------------------------------------
local WHITE = Color3.new(1, 1, 1)
local OUTLINE = C(18, 18, 26)
local GOLD = C(255, 200, 40)
local GREEN = C(60, 190, 50)
local GREEN_TEXT = C(110, 255, 80)
local BLUE = C(50, 130, 230)
local PURPLE = C(150, 70, 230)
local PINK = C(230, 80, 160)
local ORANGE = C(235, 130, 35)
local MAGENTA = C(200, 60, 200)
local GRAY = C(105, 105, 120)
local RED = C(225, 55, 55)
local ROW = C(85, 92, 150)
local BODY = C(48, 50, 66)
local SOFT = C(205, 205, 220)

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

------------------------------------------------------------------
-- СТИЛЬ: жирные обводки, объём и пиксельная текстура «как блок»
------------------------------------------------------------------
local function corner(parent, radius)
	create("UICorner", { CornerRadius = UDim.new(0, radius or 6), Parent = parent })
end

local function outline(parent, thickness, color)
	create("UIStroke", {
		Color = color or OUTLINE,
		Thickness = thickness or 3,
		ApplyStrokeMode = Enum.ApplyStrokeMode.Border,
		Parent = parent,
	})
end

local function textStroke(parent, thickness)
	return create("UIStroke", {
		Color = OUTLINE,
		Thickness = thickness or 2,
		ApplyStrokeMode = Enum.ApplyStrokeMode.Contextual,
		Parent = parent,
	})
end

local function gradient(parent, top, bottom)
	create("UIGradient", { Color = ColorSequence.new(top, bottom), Rotation = 90, Parent = parent })
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

-- Пиксельный «шум» поверх цвета — как текстура блока
local function pixelNoise(parent, cols, rows, density, transparency)
	for col = 0, cols - 1 do
		for row = 0, rows - 1 do
			if math.random() < density then
				create("Frame", {
					Name = "Pixel",
					BorderSizePixel = 0,
					BackgroundColor3 = math.random() < 0.5 and Color3.new(0, 0, 0) or WHITE,
					BackgroundTransparency = transparency or 0.88,
					Position = UDim2.fromScale(col / cols, row / rows),
					Size = UDim2.fromScale(1 / cols, 1 / rows),
					Parent = parent,
				})
			end
		end
	end
end

-- Светлая полоска сверху и тень снизу — «объёмный» блок
local function bevel(parent, inset)
	inset = inset or 0
	create("Frame", {
		Name = "Shine",
		BorderSizePixel = 0,
		BackgroundColor3 = WHITE,
		BackgroundTransparency = 0.65,
		Position = UDim2.fromOffset(4 - inset, 2 - inset),
		Size = UDim2.new(1, inset * 2 - 8, 0, 3),
		Parent = parent,
	})
	create("Frame", {
		Name = "Shade",
		BorderSizePixel = 0,
		BackgroundColor3 = Color3.new(0, 0, 0),
		BackgroundTransparency = 0.6,
		AnchorPoint = Vector2.new(0, 1),
		Position = UDim2.new(0, 4 - inset, 1, inset - 2),
		Size = UDim2.new(1, inset * 2 - 8, 0, 5),
		Parent = parent,
	})
end

local function style(gui, options)
	options = options or {}
	corner(gui, options.radius or 6)
	outline(gui, options.outline or 3)
	gradient(gui, WHITE, options.bottom or C(190, 190, 205))
	if options.noise then
		pixelNoise(gui, options.noise[1], options.noise[2], options.noise[3] or 0.12, options.noise[4])
	end
	if options.bevel ~= false then
		bevel(gui, options.inset)
	end
end

local function panel(props, color, options)
	local frame = create("Frame", with({ BackgroundColor3 = color, BorderSizePixel = 0 }, props))
	style(frame, options)
	return frame
end

local LABEL = {
	BackgroundTransparency = 1,
	Font = Enum.Font.GothamBlack,
	TextScaled = true,
	TextColor3 = WHITE,
}
local function label(props, strokeThickness)
	local text = create("TextLabel", with(LABEL, props))
	if (strokeThickness or 2) > 0 then
		textStroke(text, strokeThickness or 2)
	end
	return text
end

local function button(props, color, padding)
	padding = padding or 6
	local result = create(
		"TextButton",
		with({
			BackgroundColor3 = color,
			BorderSizePixel = 0,
			AutoButtonColor = false,
			Font = Enum.Font.GothamBlack,
			TextScaled = true,
			TextColor3 = WHITE,
			Text = "",
		}, props)
	)
	pad(result, padding)
	style(result, { inset = padding })
	textStroke(result, 2)
	-- кнопка «пружинит» при наведении и нажатии
	local scale = create("UIScale", { Parent = result })
	local function scaleTo(value)
		TweenService:Create(scale, TweenInfo.new(0.1), { Scale = value }):Play()
	end
	result.MouseEnter:Connect(function()
		scaleTo(1.05)
	end)
	result.MouseLeave:Connect(function()
		scaleTo(1)
	end)
	result.MouseButton1Down:Connect(function()
		scaleTo(0.92)
	end)
	result.MouseButton1Up:Connect(function()
		scaleTo(1.05)
	end)
	result.Activated:Connect(function()
		playSound("click")
	end)
	return result
end

local function scrolling(props)
	local frame = create(
		"ScrollingFrame",
		with({
			BackgroundTransparency = 1,
			BorderSizePixel = 0,
			ScrollBarThickness = 8,
			ScrollBarImageColor3 = OUTLINE,
			CanvasSize = UDim2.new(),
			AutomaticCanvasSize = Enum.AutomaticSize.Y,
			ScrollingDirection = Enum.ScrollingDirection.Y,
		}, props)
	)
	pad(frame, 5)
	return frame
end

local function listLayout(parent, padding, horizontal)
	create("UIListLayout", {
		Padding = UDim.new(0, padding or 8),
		FillDirection = horizontal and Enum.FillDirection.Horizontal or Enum.FillDirection.Vertical,
		SortOrder = Enum.SortOrder.LayoutOrder,
		Parent = parent,
	})
end

------------------------------------------------------------------
-- 3D-ИКОНКИ
------------------------------------------------------------------
local function modelIcon(template, parent, options)
	options = options or {}
	local viewport = create("ViewportFrame", {
		BackgroundTransparency = 1,
		Size = UDim2.fromScale(1, 1),
		Ambient = C(210, 210, 210),
		LightColor = WHITE,
		LightDirection = V(-1, -1.5, -0.5),
		Parent = parent,
	})
	if template then
		local model = template:Clone()
		model:PivotTo(options.rotation or CFrame.new())
		model.Parent = viewport
		local viewCamera = Instance.new("Camera")
		viewCamera.FieldOfView = 30
		viewCamera.Parent = viewport
		viewport.CurrentCamera = viewCamera
		local center, size = model:GetBoundingBox()
		local direction = (options.direction or V(0.6, 0.4, -1)).Unit
		viewCamera.CFrame = CFrame.lookAt(center.Position + direction * size.Magnitude * (options.zoom or 1.9), center.Position)
	end
	return viewport
end

local function petIcon(kind, parent)
	return modelIcon(petModels:FindFirstChild(kind), parent)
end

local function pickaxeIcon(tier, parent)
	return modelIcon(icons:FindFirstChild("Pickaxe_" .. tier), parent, {
		rotation = CFrame.Angles(math.rad(-40), 0, 0),
		direction = V(1, 0.15, 0),
		zoom = 1.5,
	})
end

local function blockIcon(worldId, parent)
	return modelIcon(icons:FindFirstChild("Block_" .. worldId), parent, { direction = V(1, 0.85, -1), zoom = 1.7 })
end

local function eggIcon(worldId, parent)
	return modelIcon(icons:FindFirstChild("Egg_" .. worldId), parent, { direction = V(0.5, 0.35, -1), zoom = 1.7 })
end

local function emojiIcon(emoji, parent)
	return label({ Size = UDim2.fromScale(1, 1), Text = emoji, Parent = parent }, 0)
end

------------------------------------------------------------------
-- ЭКРАН
------------------------------------------------------------------
-- Прячем стандартный инвентарь и список игроков Roblox: у нас свой интерфейс
local StarterGui = game:GetService("StarterGui")
pcall(function()
	StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.Backpack, false)
	StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.PlayerList, false)
end)

local gui = create("ScreenGui", {
	Name = "DestroyHUD",
	ResetOnSpawn = false,
	ZIndexBehavior = Enum.ZIndexBehavior.Sibling,
	Parent = player:WaitForChild("PlayerGui"),
})

local camera = workspace.CurrentCamera
local scalers = {}
local function hudScale()
	local size = camera.ViewportSize
	return math.clamp(math.min(size.Y / 860, size.X / 1300), 0.5, 1)
end
local function scaled(frame)
	local scale = create("UIScale", { Scale = hudScale(), Parent = frame })
	table.insert(scalers, scale)
end
camera:GetPropertyChangedSignal("ViewportSize"):Connect(function()
	for _, scale in scalers do
		scale.Scale = hudScale()
	end
end)

------------------------------------------------------------------
-- ВЕРХ: полоска уровня кирки
------------------------------------------------------------------
local topGroup = create("Frame", {
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 8),
	Size = UDim2.fromOffset(560, 100),
	BackgroundTransparency = 1,
	Parent = gui,
})
scaled(topGroup)

local levelBar = panel({ Size = UDim2.fromOffset(560, 60), Parent = topGroup }, C(25, 55, 105), { noise = { 70, 8, 0.12 }, outline = 4 })
local levelFill = create("Frame", {
	BorderSizePixel = 0,
	BackgroundColor3 = GOLD,
	Size = UDim2.fromScale(0, 1),
	Parent = levelBar,
})
corner(levelFill, 6)
gradient(levelFill, WHITE, C(225, 160, 40))
local levelIconHolder = create("Frame", {
	Position = UDim2.fromOffset(4, -6),
	Size = UDim2.fromOffset(64, 64),
	BackgroundTransparency = 1,
	Parent = levelBar,
})
local levelText = label({
	Position = UDim2.fromOffset(72, 8),
	Size = UDim2.new(0.5, -72, 1, -16),
	TextXAlignment = Enum.TextXAlignment.Left,
	Text = "Уровень 1",
	Parent = levelBar,
}, 3)
local levelProgress = label({
	AnchorPoint = Vector2.new(1, 0),
	Position = UDim2.new(1, -14, 0, 10),
	Size = UDim2.new(0.45, 0, 1, -20),
	TextXAlignment = Enum.TextXAlignment.Right,
	Parent = levelBar,
}, 3)
local statsLine = label({
	Position = UDim2.fromOffset(0, 66),
	Size = UDim2.new(1, 0, 0, 30),
	Parent = topGroup,
}, 2.5)

------------------------------------------------------------------
-- СЛЕВА: плитки меню и кнопка улучшения
------------------------------------------------------------------
local leftGroup = create("Frame", {
	AnchorPoint = Vector2.new(0, 0.5),
	Position = UDim2.new(0, 14, 0.46, 0),
	Size = UDim2.fromOffset(212, 392),
	BackgroundTransparency = 1,
	Parent = gui,
})
scaled(leftGroup)
local tiles = create("Frame", { Size = UDim2.fromOffset(212, 320), BackgroundTransparency = 1, Parent = leftGroup })
create("UIGridLayout", {
	CellSize = UDim2.fromOffset(100, 100),
	CellPadding = UDim2.fromOffset(12, 10),
	SortOrder = Enum.SortOrder.LayoutOrder,
	Parent = tiles,
})

local function tile(order, title, color, buildIcon)
	local result = button({ LayoutOrder = order, Parent = tiles }, color, 0)
	local holder = create("Frame", {
		Position = UDim2.fromOffset(10, 4),
		Size = UDim2.new(1, -20, 1, -30),
		BackgroundTransparency = 1,
		Parent = result,
	})
	buildIcon(holder)
	label({
		AnchorPoint = Vector2.new(0.5, 1),
		Position = UDim2.new(0.5, 0, 1, -4),
		Size = UDim2.new(1, -6, 0, 24),
		Text = title,
		Parent = result,
	}, 2.5)
	local badge = label({
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.new(1, -6, 0, 6),
		Size = UDim2.fromOffset(30, 30),
		Text = "!",
		TextColor3 = C(255, 60, 60),
		Visible = false,
		ZIndex = 3,
		Parent = result,
	}, 3)
	return result, holder, badge
end

local pickaxesTile, pickaxesTileIcon, pickaxesBadge = tile(1, "Кирки", ORANGE, function(holder)
	pickaxeIcon(1, holder)
end)
local petsTile = tile(2, "Питомцы", PINK, function(holder)
	petIcon("Волк", holder)
end)
local eggsTile = tile(3, "Яйца", C(235, 185, 30), function(holder)
	eggIcon(1, holder)
end)
local worldsTile = tile(4, "Миры", BLUE, function(holder)
	blockIcon(1, holder)
end)
local tradeTile = tile(5, "Трейд", PURPLE, function(holder)
	emojiIcon("🤝", holder)
end)
local rebirthTile, _, rebirthBadge = tile(6, "Ребёрт", MAGENTA, function(holder)
	emojiIcon("🔁", holder)
end)

local upgradeButton = button({
	Position = UDim2.fromOffset(0, 330),
	Size = UDim2.fromOffset(146, 62),
	Parent = leftGroup,
}, GREEN, 6)
local maxButton = button({
	Position = UDim2.fromOffset(154, 330),
	Size = UDim2.fromOffset(58, 62),
	Text = "МАКС",
	Parent = leftGroup,
}, GREEN, 6)

------------------------------------------------------------------
-- СЛЕВА ВНИЗУ: монеты, ребёрты, питомцы
------------------------------------------------------------------
local statsGroup = create("Frame", {
	AnchorPoint = Vector2.new(0, 1),
	Position = UDim2.new(0, 14, 1, -14),
	Size = UDim2.fromOffset(320, 156),
	BackgroundTransparency = 1,
	Parent = gui,
})
scaled(statsGroup)
listLayout(statsGroup, 2)

local function statRow(order, emoji, color)
	local row = create("Frame", { LayoutOrder = order, Size = UDim2.new(1, 0, 0, 50), BackgroundTransparency = 1, Parent = statsGroup })
	label({ Size = UDim2.fromOffset(50, 50), Text = emoji, Parent = row }, 0)
	local value = label({
		Position = UDim2.fromOffset(58, 2),
		Size = UDim2.new(1, -58, 1, -4),
		TextXAlignment = Enum.TextXAlignment.Left,
		TextColor3 = color,
		Parent = row,
	}, 3)
	return row, value
end
local coinsRow, coinsText = statRow(1, "💰", GREEN_TEXT)
local coinsScale = create("UIScale", { Parent = coinsRow })
local _, rebirthsText = statRow(2, "🔁", C(255, 130, 230))
local _, petsText = statRow(3, "🐾", C(120, 210, 255))

------------------------------------------------------------------
-- СПРАВА: Золотая лихорадка
------------------------------------------------------------------
local rightGroup = create("Frame", {
	AnchorPoint = Vector2.new(1, 0.5),
	Position = UDim2.new(1, -14, 0.42, 0),
	Size = UDim2.fromOffset(240, 116),
	BackgroundTransparency = 1,
	Parent = gui,
})
scaled(rightGroup)
local eventHeader = panel({ Size = UDim2.new(1, 0, 0, 40), Parent = rightGroup }, C(45, 35, 30), { noise = { 30, 5, 0.15 } })
label({ Position = UDim2.fromOffset(8, 5), Size = UDim2.new(1, -16, 1, -10), Text = "🔥 ЛИХОРАДКА", TextColor3 = GOLD, Parent = eventHeader }, 2.5)
local eventBody = panel({ Position = UDim2.fromOffset(0, 46), Size = UDim2.new(1, 0, 0, 70), Parent = rightGroup }, ORANGE, { noise = { 30, 9, 0.12 } })
local eventText = label({ Position = UDim2.fromOffset(10, 8), Size = UDim2.new(1, -20, 1, -16), Parent = eventBody }, 3)

------------------------------------------------------------------
-- ЦЕНТР: комбо и объявления
------------------------------------------------------------------
local comboLabel = label({
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.7),
	Size = UDim2.fromOffset(340, 64),
	Visible = false,
	Parent = gui,
}, 3.5)
local comboScale = create("UIScale", { Parent = comboLabel })

local banner = label({
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.new(0.5, 0, 0, 150),
	Size = UDim2.new(0.8, 0, 0, 50),
	TextTransparency = 1,
	ZIndex = 30,
	Parent = gui,
}, 3)
local bannerScale = create("UIScale", { Parent = banner })
local bannerStroke = banner:FindFirstChildOfClass("UIStroke") :: UIStroke
bannerStroke.Transparency = 1

------------------------------------------------------------------
-- ОКНА
------------------------------------------------------------------
local windows = {}
local openName = nil

local function fitScale()
	local size = camera.ViewportSize
	return math.clamp(math.min((size.X - 20) / 660, (size.Y - 120) / 460), 0.5, 1)
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

local function makeWindow(name, title, accent, buildIcon)
	local frame = panel({
		Name = name,
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.5),
		Size = UDim2.fromOffset(640, 450),
		Visible = false,
		ZIndex = 5,
		Parent = gui,
	}, BODY, { outline = 4, noise = { 64, 45, 0.05 }, bevel = false, bottom = C(150, 150, 170) })
	local scale = create("UIScale", { Parent = frame })
	local header = panel({ Size = UDim2.new(1, 0, 0, 64), Parent = frame }, accent, { outline = 4, noise = { 64, 8, 0.14 } })
	local iconHolder = create("Frame", { Position = UDim2.fromOffset(8, 4), Size = UDim2.fromOffset(56, 56), BackgroundTransparency = 1, Parent = header })
	buildIcon(iconHolder)
	local titleLabel = label({
		Position = UDim2.fromOffset(72, 9),
		Size = UDim2.new(1, -140, 1, -18),
		Text = title,
		TextXAlignment = Enum.TextXAlignment.Left,
		Parent = header,
	}, 3.5)
	local close = button({
		AnchorPoint = Vector2.new(1, 0.5),
		Position = UDim2.new(1, -10, 0.5, 0),
		Size = UDim2.fromOffset(48, 48),
		Text = "X",
		Parent = header,
	}, RED, 5)
	local body = create("Frame", {
		Position = UDim2.fromOffset(12, 76),
		Size = UDim2.new(1, -24, 1, -88),
		BackgroundTransparency = 1,
		Parent = frame,
	})
	local window = {
		frame = frame,
		body = body,
		title = titleLabel,
		scale = scale,
		render = function() end,
		update = function() end,
		onClose = nil,
	}
	windows[name] = window
	close.Activated:Connect(closeWindows)
	return window
end

local function listRow(parent, order, height, color)
	return panel({ LayoutOrder = order, Size = UDim2.new(1, -14, 0, height), Parent = parent }, color or ROW)
end

local function iconBox(parent, size, buildIcon)
	local box = panel({
		Position = UDim2.fromOffset(9, 9),
		Size = UDim2.fromOffset(size, size),
		Parent = parent,
	}, C(235, 235, 242), { bevel = false, bottom = C(200, 200, 215) })
	buildIcon(box)
	return box
end

local function sectionTitle(parent, order, text)
	label({
		LayoutOrder = order,
		Size = UDim2.new(1, -14, 0, 32),
		Text = text,
		TextColor3 = GOLD,
		Parent = parent,
	}, 2.5)
end

local function rowTexts(row, left, title, titleColor, line1, line1Color, line2)
	label({
		Position = UDim2.fromOffset(left, 8),
		Size = UDim2.new(1, -(left + 200), 0, 32),
		Text = title,
		TextColor3 = titleColor or WHITE,
		TextXAlignment = Enum.TextXAlignment.Left,
		Parent = row,
	}, 2.5)
	if line1 then
		label({
			Position = UDim2.fromOffset(left, 42),
			Size = UDim2.new(1, -(left + 200), 0, 24),
			Text = line1,
			TextColor3 = line1Color or GOLD,
			TextXAlignment = Enum.TextXAlignment.Left,
			Parent = row,
		}, 2)
	end
	if line2 then
		label({
			Position = UDim2.fromOffset(left, 66),
			Size = UDim2.new(1, -(left + 200), 0, 20),
			Text = line2,
			TextColor3 = SOFT,
			TextXAlignment = Enum.TextXAlignment.Left,
			Parent = row,
		}, 1.5)
	end
end

local function rowButton(row)
	return button({
		AnchorPoint = Vector2.new(1, 0.5),
		Position = UDim2.new(1, -12, 0.5, 0),
		Size = UDim2.fromOffset(170, 58),
		Parent = row,
	}, GRAY, 6)
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

-- Карточка питомца в цвете редкости
local function petCard(kind, parent, order, equipped)
	local info = INFO.pets[kind]
	local rarity = INFO.rarities[info.rarity]
	local card = create("TextButton", {
		LayoutOrder = order,
		Text = "",
		AutoButtonColor = false,
		BorderSizePixel = 0,
		BackgroundColor3 = rarity.color:Lerp(Color3.new(0, 0, 0), 0.3),
		Parent = parent,
	})
	style(card, { bottom = C(150, 150, 170) })
	label({ Position = UDim2.fromScale(0.06, 0.04), Size = UDim2.fromScale(0.88, 0.17), Text = kind, Parent = card }, 2)
	local holder = create("Frame", {
		Position = UDim2.fromScale(0.05, 0.2),
		Size = UDim2.fromScale(0.9, 0.56),
		BackgroundTransparency = 1,
		Parent = card,
	})
	petIcon(kind, holder)
	label({
		Position = UDim2.fromScale(0.06, 0.77),
		Size = UDim2.fromScale(0.88, 0.18),
		Text = "+" .. percent(info.bonus),
		TextColor3 = GREEN_TEXT,
		Parent = card,
	}, 2)
	if equipped then
		local check = panel({ Position = UDim2.fromOffset(-6, -6), Size = UDim2.fromOffset(26, 26), ZIndex = 3, Parent = card }, GREEN, { bevel = false })
		label({ Size = UDim2.fromScale(1, 1), Text = "✔", ZIndex = 3, Parent = check }, 1.5)
	end
	return card
end

------------------------------------------------------------------
-- ОКНО: КИРКИ И АДДОНЫ
------------------------------------------------------------------
local pickWindow = makeWindow("pickaxes", "Кирки и аддоны", ORANGE, function(holder)
	pickaxeIcon(1, holder)
end)
local pickList = scrolling({ Size = UDim2.fromScale(1, 1), Parent = pickWindow.body })
listLayout(pickList, 10)
local pickButtons = {}

pickWindow.update = function()
	local coins = coinsValue.Value
	local current = player:GetAttribute("Pickaxe") or 1
	local cap = INFO.pickaxes[current].addonCap
	for _, entry in pickButtons do
		local target = entry.button
		if entry.tier then
			local pick = INFO.pickaxes[entry.tier]
			if entry.tier < current then
				target.Text = "✔ ЕСТЬ"
				target.BackgroundColor3 = GRAY
			elseif entry.tier == current then
				target.Text = "В РУКАХ"
				target.BackgroundColor3 = BLUE
			elseif entry.tier == current + 1 then
				target.Text = "КУПИТЬ\n💰 " .. abbreviate(pick.price)
				target.BackgroundColor3 = coins >= pick.price and GREEN or GRAY
			else
				target.Text = "🔒"
				target.BackgroundColor3 = GRAY
			end
		else
			local level = player:GetAttribute("Addon_" .. entry.addon.id) or 0
			if level >= cap then
				target.Text = cap >= INFO.pickaxes[#INFO.pickaxes].addonCap and "MAX" or "НУЖНА КИРКА\nЛУЧШЕ"
				target.BackgroundColor3 = GRAY
			else
				local cost = math.floor(entry.addon.baseCost * entry.addon.growth ^ level)
				target.Text = "УЛУЧШИТЬ\n💰 " .. abbreviate(cost)
				target.BackgroundColor3 = coins >= cost and GREEN or GRAY
			end
		end
	end
end

pickWindow.render = function()
	clear(pickList)
	pickButtons = {}
	local current = player:GetAttribute("Pickaxe") or 1
	local cap = INFO.pickaxes[current].addonCap
	local order = 0
	local function nextOrder()
		order += 1
		return order
	end

	sectionTitle(pickList, nextOrder(), "⛏ КИРКИ")
	for tier, pick in INFO.pickaxes do
		local row = listRow(pickList, nextOrder(), 96, tier == current and C(70, 175, 55) or ROW)
		iconBox(row, 78, function(box)
			pickaxeIcon(tier, box)
		end)
		rowTexts(row, 100, pick.name, pick.color:Lerp(WHITE, 0.25), "⛏ Урон x" .. abbreviate(pick.damage), GOLD, "Аддоны до ур. " .. pick.addonCap)
		local target = rowButton(row)
		if tier == current + 1 then
			target.Activated:Connect(function()
				shopRemote:FireServer("pickaxe")
			end)
		end
		table.insert(pickButtons, { button = target, tier = tier })
	end

	sectionTitle(pickList, nextOrder(), "✨ АДДОНЫ  (до ур. " .. cap .. " с твоей киркой)")
	for _, addon in INFO.addons do
		local level = player:GetAttribute("Addon_" .. addon.id) or 0
		local row = listRow(pickList, nextOrder(), 96)
		local emoji, name = addon.name:match("^(%S+)%s+(.+)$")
		iconBox(row, 78, function(box)
			emojiIcon(emoji or "✨", box)
		end)
		rowTexts(row, 100, (name or addon.name) .. "  ур. " .. level, WHITE, nil, nil, nil)
		-- шкала уровня: 10 квадратиков
		local pips = create("Frame", { Position = UDim2.fromOffset(100, 44), Size = UDim2.fromOffset(250, 18), BackgroundTransparency = 1, Parent = row })
		listLayout(pips, 4, true)
		for i = 1, 10 do
			local pip = create("Frame", {
				LayoutOrder = i,
				Size = UDim2.fromOffset(20, 18),
				BorderSizePixel = 0,
				BackgroundColor3 = i <= level and GOLD or (i <= cap and C(40, 40, 55) or C(70, 70, 80)),
				BackgroundTransparency = i <= cap and 0 or 0.6,
				Parent = pips,
			})
			outline(pip, 2)
		end
		label({
			Position = UDim2.fromOffset(100, 66),
			Size = UDim2.new(1, -300, 0, 20),
			Text = addon.desc,
			TextColor3 = SOFT,
			TextXAlignment = Enum.TextXAlignment.Left,
			Parent = row,
		}, 1.5)
		local target = rowButton(row)
		target.Activated:Connect(function()
			shopRemote:FireServer("addon", addon.id)
		end)
		table.insert(pickButtons, { button = target, addon = addon })
	end
	pickWindow.update()
end

------------------------------------------------------------------
-- ОКНО: ПИТОМЦЫ
------------------------------------------------------------------
local petsWindow = makeWindow("pets", "Питомцы", PINK, function(holder)
	petIcon("Волк", holder)
end)
local petsInfo = label({
	Size = UDim2.new(1, -200, 0, 34),
	TextXAlignment = Enum.TextXAlignment.Left,
	Parent = petsWindow.body,
}, 2)
local bestButton = button({
	AnchorPoint = Vector2.new(1, 0),
	Position = UDim2.new(1, -4, 0, 0),
	Size = UDim2.fromOffset(190, 40),
	Text = "⭐ НАДЕТЬ ЛУЧШИХ",
	Parent = petsWindow.body,
}, C(220, 160, 20), 5)
bestButton.Activated:Connect(function()
	petRemote:FireServer("best")
end)
local petsGrid = scrolling({ Position = UDim2.fromOffset(0, 48), Size = UDim2.new(1, 0, 1, -48), Parent = petsWindow.body })
create("UIGridLayout", {
	CellSize = UDim2.fromOffset(108, 136),
	CellPadding = UDim2.fromOffset(10, 10),
	SortOrder = Enum.SortOrder.LayoutOrder,
	Parent = petsGrid,
})

petsWindow.render = function()
	clear(petsGrid)
	petsInfo.Text = string.format(
		"🐾 %d/%d   ✔ %d/%d   💰 +%s",
		#inventory.pets,
		INFO.maxPets,
		#inventory.equipped,
		INFO.maxEquipped,
		percent(player:GetAttribute("PetBonus") or 0)
	)
	if #inventory.pets == 0 then
		label({ Size = UDim2.fromOffset(560, 34), Text = "Пока пусто. Открой яйцо в меню «Яйца»!", Parent = petsGrid }, 2)
		return
	end
	for index, pet in sortedPets() do
		local equipped = equippedSet[pet.id] == true
		local card = petCard(pet.kind, petsGrid, index, equipped)
		card.Activated:Connect(function()
			playSound("click")
			petRemote:FireServer(equipped and "unequip" or "equip", pet.id)
		end)
		local trash = button({
			AnchorPoint = Vector2.new(1, 0),
			Position = UDim2.new(1, 4, 0, -4),
			Size = UDim2.fromOffset(28, 28),
			Text = "🗑",
			ZIndex = 3,
			Parent = card,
		}, C(80, 80, 95), 3)
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
					trash.BackgroundColor3 = C(80, 80, 95)
					trash.Text = "🗑"
				end
			end)
		end)
	end
end

------------------------------------------------------------------
-- ОКНО: ЯЙЦА (карточки, как в магазине)
------------------------------------------------------------------
local eggsWindow = makeWindow("eggs", "Яйца", C(235, 185, 30), function(holder)
	eggIcon(1, holder)
end)
local eggsRow = create("Frame", { Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1, Parent = eggsWindow.body })
listLayout(eggsRow, 12, true)
local eggButtons = {}

eggsWindow.update = function()
	local unlocked = unlockedWorlds()
	for id, entry in eggButtons do
		if not unlocked[id] then
			entry.button.Text = "🔒 " .. entry.world.name
			entry.button.BackgroundColor3 = GRAY
		else
			entry.button.Text = "ОТКРЫТЬ"
			entry.button.BackgroundColor3 = coinsValue.Value >= entry.world.egg.price and GREEN or GRAY
		end
	end
end

eggsWindow.render = function()
	if next(eggButtons) then
		eggsWindow.update()
		return
	end
	for _, world in INFO.worlds do
		local card = panel({
			LayoutOrder = world.id,
			Size = UDim2.fromOffset(196, 352),
			Parent = eggsRow,
		}, world.accent:Lerp(Color3.new(0, 0, 0), 0.25), { noise = { 20, 36, 0.08 }, bottom = C(140, 140, 160) })
		label({ Position = UDim2.fromOffset(8, 8), Size = UDim2.new(1, -16, 0, 28), Text = world.egg.name, Parent = card }, 2.5)
		local holder = create("Frame", { Position = UDim2.fromOffset(38, 36), Size = UDim2.fromOffset(120, 106), BackgroundTransparency = 1, Parent = card })
		eggIcon(world.id, holder)
		label({
			Position = UDim2.fromOffset(8, 142),
			Size = UDim2.new(1, -16, 0, 32),
			Text = "💰 " .. abbreviate(world.egg.price),
			TextColor3 = GREEN_TEXT,
			Parent = card,
		}, 3)
		local chances = create("Frame", { Position = UDim2.fromOffset(10, 178), Size = UDim2.new(1, -20, 0, 104), BackgroundTransparency = 1, Parent = card })
		create("UIGridLayout", {
			CellSize = UDim2.fromOffset(54, 50),
			CellPadding = UDim2.fromOffset(4, 4),
			HorizontalAlignment = Enum.HorizontalAlignment.Center,
			SortOrder = Enum.SortOrder.LayoutOrder,
			Parent = chances,
		})
		for index, entry in world.egg.pets do
			local rarity = INFO.rarities[INFO.pets[entry.kind].rarity]
			local mini = panel({ LayoutOrder = index, Parent = chances }, rarity.color:Lerp(Color3.new(0, 0, 0), 0.35), { outline = 2, bevel = false })
			local miniHolder = create("Frame", { Position = UDim2.fromOffset(2, 0), Size = UDim2.new(1, -4, 0, 34), BackgroundTransparency = 1, Parent = mini })
			petIcon(entry.kind, miniHolder)
			local chance = entry.chance < 1 and string.format("%.1f%%", entry.chance) or (math.floor(entry.chance + 0.5) .. "%")
			label({ Position = UDim2.fromOffset(2, 32), Size = UDim2.new(1, -4, 0, 16), Text = chance, Parent = mini }, 1.5)
		end
		local open = button({
			Position = UDim2.fromOffset(12, 290),
			Size = UDim2.new(1, -24, 0, 50),
			Parent = card,
		}, GREEN, 6)
		open.Activated:Connect(function()
			openEggRemote:FireServer(world.id)
		end)
		eggButtons[world.id] = { button = open, world = world }
	end
	eggsWindow.update()
end

do
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
	local eggStage = create("Frame", {
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.45),
		Size = UDim2.fromOffset(320, 380),
		BackgroundTransparency = 1,
		Parent = eggOverlay,
	})
	local eggStageScale = create("UIScale", { Parent = eggStage })
	local eggHolder = create("Frame", { Size = UDim2.fromOffset(320, 300), BackgroundTransparency = 1, Parent = eggStage })
	local eggGlow = create("Frame", {
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromOffset(160, 130),
		Size = UDim2.fromOffset(260, 260),
		BorderSizePixel = 0,
		BackgroundTransparency = 0.5,
		Visible = false,
		Parent = eggStage,
	})
	corner(eggGlow, 130)
	local petHolder = create("Frame", { Size = UDim2.fromOffset(320, 260), BackgroundTransparency = 1, Parent = eggStage })
	local eggResultName = label({ Position = UDim2.fromOffset(0, 266), Size = UDim2.fromOffset(320, 50), Parent = eggStage }, 3.5)
	local eggResultInfo = label({ Position = UDim2.fromOffset(0, 320), Size = UDim2.fromOffset(320, 34), Parent = eggStage }, 2.5)
	local flash = create("Frame", {
		Size = UDim2.fromScale(1, 1),
		BackgroundColor3 = WHITE,
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		ZIndex = 25,
		Parent = gui,
	})
	local eggToken = 0

	eggOverlay.Activated:Connect(function()
		eggToken += 1
		eggOverlay.Visible = false
	end)

	eggResultRemote.OnClientEvent:Connect(function(kind, worldId)
		eggToken += 1
		local token = eggToken
		eggOverlay.Visible = true
		eggGlow.Visible = false
		eggStageScale.Scale = 1
		eggResultName.Text = ""
		eggResultInfo.Text = ""
		clear(petHolder)
		clear(eggHolder)
		local egg = eggIcon(worldId or 1, eggHolder)
		for i = 1, 4 do
			local angle = 10 + i * 4
			TweenService:Create(egg, TweenInfo.new(0.09), { Rotation = -angle }):Play()
			task.wait(0.1)
			TweenService:Create(egg, TweenInfo.new(0.09), { Rotation = angle }):Play()
			task.wait(0.1)
			if token ~= eggToken then
				return
			end
		end
		flash.BackgroundTransparency = 0
		TweenService:Create(flash, TweenInfo.new(0.5), { BackgroundTransparency = 1 }):Play()
		clear(eggHolder)

		local info = INFO.pets[kind]
		local rarity = INFO.rarities[info.rarity]
		eggGlow.BackgroundColor3 = rarity.color
		eggGlow.Visible = true
		petIcon(kind, petHolder)
		eggResultName.Text = kind
		eggResultName.TextColor3 = rarity.color
		eggResultInfo.Text = info.rarity .. " · +" .. percent(info.bonus) .. " монет"
		eggStageScale.Scale = 0.4
		TweenService:Create(eggStageScale, TweenInfo.new(0.4, Enum.EasingStyle.Back, Enum.EasingDirection.Out), { Scale = 1 }):Play()
		playSound("reveal")
		task.wait(2.6)
		if token == eggToken then
			eggOverlay.Visible = false
		end
	end)

end
------------------------------------------------------------------
-- ОКНО: МИРЫ
------------------------------------------------------------------
local worldsWindow = makeWindow("worlds", "Миры", BLUE, function(holder)
	blockIcon(1, holder)
end)
local worldsList = scrolling({ Size = UDim2.fromScale(1, 1), Parent = worldsWindow.body })
listLayout(worldsList, 10)
local worldButtons = {}

worldsWindow.update = function()
	local unlocked = unlockedWorlds()
	local current = player:GetAttribute("World") or 1
	for id, entry in worldButtons do
		if id == current then
			entry.button.Text = "📍 ТЫ ЗДЕСЬ"
			entry.button.BackgroundColor3 = GRAY
		elseif unlocked[id] then
			entry.button.Text = "ТЕЛЕПОРТ"
			entry.button.BackgroundColor3 = BLUE
		else
			entry.button.Text = "ОТКРЫТЬ\n💰 " .. abbreviate(entry.world.price)
			entry.button.BackgroundColor3 = coinsValue.Value >= entry.world.price and GREEN or GRAY
		end
		entry.status.Text = unlocked[id] and "✔ Открыт" or "🔒 Закрыт"
		entry.status.TextColor3 = unlocked[id] and GREEN_TEXT or SOFT
	end
end

worldsWindow.render = function()
	if next(worldButtons) then
		worldsWindow.update()
		return
	end
	for _, world in INFO.worlds do
		local row = listRow(worldsList, world.id, 104, world.accent:Lerp(Color3.new(0, 0, 0), 0.3))
		iconBox(row, 86, function(box)
			blockIcon(world.id, box)
		end)
		rowTexts(row, 108, world.name, WHITE, nil, nil, "Яйцо: " .. world.egg.name)
		local status = label({
			Position = UDim2.fromOffset(108, 44),
			Size = UDim2.new(1, -308, 0, 24),
			TextXAlignment = Enum.TextXAlignment.Left,
			Parent = row,
		}, 2)
		local target = rowButton(row)
		target.Activated:Connect(function()
			if world.id ~= (player:GetAttribute("World") or 1) then
				worldRemote:FireServer(world.id)
			end
		end)
		worldButtons[world.id] = { button = target, status = status, world = world }
	end
	worldsWindow.update()
end

------------------------------------------------------------------
-- ОКНО: РЕБЁРТ (до и после)
------------------------------------------------------------------
local rebirthWindow = makeWindow("rebirth", "Ребёрт", MAGENTA, function(holder)
	emojiIcon("🔁", holder)
end)
label({ Position = UDim2.fromOffset(0, 0), Size = UDim2.new(0.45, 0, 0, 32), Text = "Сейчас:", Parent = rebirthWindow.body }, 2.5)
label({ Position = UDim2.new(0.55, 0, 0, 0), Size = UDim2.new(0.45, 0, 0, 32), Text = "После:", Parent = rebirthWindow.body }, 2.5)

do
	local function compareRow(y, color)
		local body = rebirthWindow.body
		local before = panel({ Position = UDim2.fromOffset(0, y), Size = UDim2.new(0.45, 0, 0, 62), Parent = body }, color, { noise = { 30, 8, 0.1 } })
		local beforeText = label({ Position = UDim2.fromOffset(8, 8), Size = UDim2.new(1, -16, 1, -16), Parent = before }, 3)
		label({ Position = UDim2.new(0.45, 0, 0, y + 8), Size = UDim2.new(0.1, 0, 0, 46), Text = "▶", Parent = body }, 2.5)
		local after = panel({ Position = UDim2.new(0.55, 0, 0, y), Size = UDim2.new(0.45, 0, 0, 62), Parent = body }, color, { noise = { 30, 8, 0.1 } })
		local afterText = label({ Position = UDim2.fromOffset(8, 8), Size = UDim2.new(1, -16, 1, -16), Parent = after }, 3)
		return beforeText, afterText
	end
	local multBefore, multAfter = compareRow(40, C(60, 170, 50))
	local countBefore, countAfter = compareRow(112, C(200, 150, 30))
	label({
		Position = UDim2.fromOffset(0, 184),
		Size = UDim2.new(1, 0, 0, 26),
		Text = "*Ребёрт сбросит монеты и уровень кирки*",
		TextColor3 = C(255, 80, 80),
		Parent = rebirthWindow.body,
	}, 2)
	label({
		Position = UDim2.fromOffset(0, 212),
		Size = UDim2.new(1, 0, 0, 22),
		Text = "Кирки, аддоны, питомцы и миры останутся ✔",
		TextColor3 = SOFT,
		Parent = rebirthWindow.body,
	}, 1.5)
	local rebirthBar = panel({ Position = UDim2.fromOffset(0, 244), Size = UDim2.new(1, 0, 0, 46), Parent = rebirthWindow.body }, C(30, 30, 40), { bevel = false })
	local rebirthFill = create("Frame", { BorderSizePixel = 0, BackgroundColor3 = GOLD, Size = UDim2.fromScale(0, 1), Parent = rebirthBar })
	corner(rebirthFill, 6)
	gradient(rebirthFill, WHITE, C(225, 160, 40))
	local rebirthBarText = label({ Position = UDim2.fromOffset(8, 6), Size = UDim2.new(1, -16, 1, -12), Parent = rebirthBar }, 2.5)
	local rebirthButton = button({ Position = UDim2.fromOffset(0, 302), Size = UDim2.new(1, 0, 0, 58), Text = "🔁 РЕБЁРТ", Parent = rebirthWindow.body }, GREEN, 8)
	rebirthButton.Activated:Connect(function()
		shopRemote:FireServer("rebirth")
	end)

	rebirthWindow.update = function()
		local rebirths = rebirthsValue.Value
		local cost = player:GetAttribute("RebirthCost") or 1
		local bonus = INFO.rebirthBonus
		multBefore.Text = "💰 x" .. formatMultiplier(1 + rebirths * bonus)
		multAfter.Text = "💰 x" .. formatMultiplier(1 + (rebirths + 1) * bonus)
		countBefore.Text = "🔁 " .. rebirths
		countAfter.Text = "🔁 " .. (rebirths + 1)
		local coins = coinsValue.Value
		rebirthFill.Size = UDim2.fromScale(math.clamp(coins / cost, 0, 1), 1)
		rebirthBarText.Text = "💰 " .. abbreviate(coins) .. " / " .. abbreviate(cost)
		rebirthButton.BackgroundColor3 = coins >= cost and GREEN or GRAY
	end
	rebirthWindow.render = rebirthWindow.update
end

------------------------------------------------------------------
-- АДМИН-ПАНЕЛЬ (видна только админам)
------------------------------------------------------------------
local adminWindow = makeWindow("admin", "Админ-панель", C(200, 40, 40), function(holder)
	emojiIcon("🛠", holder)
end)
do
	local list = scrolling({ Size = UDim2.fromScale(1, 1), Parent = adminWindow.body })
	create("UIGridLayout", {
		CellSize = UDim2.fromOffset(190, 56),
		CellPadding = UDim2.fromOffset(10, 10),
		SortOrder = Enum.SortOrder.LayoutOrder,
		Parent = list,
	})
	local order = 0
	local function adminButton(text, color, action, arg)
		order += 1
		local target = button({ LayoutOrder = order, Text = text, Parent = list }, color, 6)
		target.Activated:Connect(function()
			adminRemote:FireServer(action, arg)
		end)
	end
	adminButton("💰 +1K", GREEN, "coins", 1e3)
	adminButton("💰 +1M", GREEN, "coins", 1e6)
	adminButton("💰 +1B", GREEN, "coins", 1e9)
	adminButton("💰 +1T", GREEN, "coins", 1e12)
	adminButton("💰 Обнулить", GRAY, "resetCoins")
	adminButton("🔁 +1 ребёрт", MAGENTA, "rebirth")
	adminButton("⛏ +10 уровней", ORANGE, "level")
	adminButton("⛏ Лучшая кирка", ORANGE, "pickaxe")
	adminButton("🌍 Все миры", BLUE, "worlds")
	local kinds = {}
	for kind in INFO.pets do
		table.insert(kinds, kind)
	end
	table.sort(kinds, function(a, b)
		return INFO.pets[a].bonus < INFO.pets[b].bonus
	end)
	for _, kind in kinds do
		adminButton("🐾 " .. kind, INFO.rarities[INFO.pets[kind].rarity].color:Lerp(Color3.new(0, 0, 0), 0.3), "pet", kind)
	end
end

local adminButtonHud = button({
	AnchorPoint = Vector2.new(1, 0),
	Position = UDim2.new(1, -14, 0.42, 70),
	Size = UDim2.fromOffset(140, 46),
	Text = "🛠 АДМИН",
	Visible = player:GetAttribute("IsAdmin") == true,
	Parent = gui,
}, C(200, 40, 40), 6)
scaled(adminButtonHud)
adminButtonHud.Activated:Connect(function()
	toggleWindow("admin")
end)
player:GetAttributeChangedSignal("IsAdmin"):Connect(function()
	adminButtonHud.Visible = player:GetAttribute("IsAdmin") == true
end)

------------------------------------------------------------------
-- ОКНО: ТРЕЙД
------------------------------------------------------------------
local tradeWindow = makeWindow("trade", "Трейд", PURPLE, function(holder)
	emojiIcon("🤝", holder)
end)
local tradeState = nil
local countdownStartedAt = 0

local tradeLobby = create("Frame", { Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1, Parent = tradeWindow.body })
label({ Size = UDim2.new(1, 0, 0, 30), Text = "С кем меняемся питомцами?", Parent = tradeLobby }, 2.5)
local tradePlayers = scrolling({ Position = UDim2.fromOffset(0, 38), Size = UDim2.new(1, 0, 1, -38), Parent = tradeLobby })
listLayout(tradePlayers, 8)

local tradeActive = create("Frame", { Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1, Visible = false, Parent = tradeWindow.body })

local function offerColumn(x, title, color)
	local column = panel({
		Position = UDim2.new(x, x > 0 and 6 or 0, 0, 0),
		Size = UDim2.new(0.5, -6, 0, 156),
		Parent = tradeActive,
	}, color, { bevel = false })
	local titleLabel = label({ Position = UDim2.fromOffset(8, 4), Size = UDim2.new(1, -16, 0, 24), Text = title, Parent = column }, 2)
	local grid = scrolling({ Position = UDim2.fromOffset(4, 30), Size = UDim2.new(1, -8, 1, -34), Parent = column })
	create("UIGridLayout", { CellSize = UDim2.fromOffset(66, 84), CellPadding = UDim2.fromOffset(6, 6), SortOrder = Enum.SortOrder.LayoutOrder, Parent = grid })
	return titleLabel, grid
end
local _, mineGrid = offerColumn(0, "Ты отдаёшь", C(60, 90, 160))
local theirTitle, theirGrid = offerColumn(0.5, "Партнёр отдаёт", C(120, 70, 160))

local tradeStatus = label({ Position = UDim2.fromOffset(0, 162), Size = UDim2.new(1, 0, 0, 26), Parent = tradeActive }, 2)
local inventoryPanel = panel({ Position = UDim2.fromOffset(0, 194), Size = UDim2.new(1, 0, 0, 108), Parent = tradeActive }, C(40, 42, 56), { bevel = false })
label({ Position = UDim2.fromOffset(8, 2), Size = UDim2.new(1, -16, 0, 20), Text = "Твои питомцы: нажми, чтобы добавить", TextColor3 = SOFT, Parent = inventoryPanel }, 1.5)
local tradeInventory = scrolling({ Position = UDim2.fromOffset(4, 22), Size = UDim2.new(1, -8, 1, -24), Parent = inventoryPanel })
create("UIGridLayout", { CellSize = UDim2.fromOffset(66, 84), CellPadding = UDim2.fromOffset(6, 6), SortOrder = Enum.SortOrder.LayoutOrder, Parent = tradeInventory })
local readyButton = button({ Position = UDim2.fromOffset(0, 310), Size = UDim2.new(0.5, -6, 0, 50), Parent = tradeActive }, GREEN, 7)
local cancelButton = button({ Position = UDim2.new(0.5, 6, 0, 310), Size = UDim2.new(0.5, -6, 0, 50), Text = "❌ ОТМЕНА", Parent = tradeActive }, RED, 7)

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
		tradeWindow.title.Text = "Трейд с " .. tradeState.partner
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
		readyButton.Text = tradeState.myReady and "↩ НЕ ГОТОВ" or "✅ ГОТОВ"
		readyButton.BackgroundColor3 = tradeState.myReady and GRAY or GREEN
		updateTradeStatus()
	else
		tradeLobby.Visible = true
		tradeActive.Visible = false
		tradeWindow.title.Text = "Трейд"
		clear(tradePlayers)
		local count = 0
		for _, other in Players:GetPlayers() do
			if other ~= player then
				count += 1
				local row = listRow(tradePlayers, count, 76)
				label({
					Position = UDim2.fromOffset(16, 0),
					Size = UDim2.new(1, -220, 1, 0),
					Text = other.DisplayName,
					TextXAlignment = Enum.TextXAlignment.Left,
					Parent = row,
				}, 2.5)
				local target = rowButton(row)
				target.Text = "ПРЕДЛОЖИТЬ"
				target.BackgroundColor3 = PURPLE
				target.Activated:Connect(function()
					tradeRemote:FireServer("request", other.UserId)
				end)
			end
		end
		if count == 0 then
			label({ Size = UDim2.new(1, -14, 0, 34), Text = "Пока нет других игроков. Позови друга! 🙂", Parent = tradePlayers }, 2)
		end
	end
end

tradeWindow.onClose = function()
	if tradeState then
		tradeRemote:FireServer("cancel")
	end
end

-- Всплывашка «тебе предлагают трейд»
local toast = panel({
	AnchorPoint = Vector2.new(1, 0),
	Position = UDim2.new(1, -14, 0.58, 0),
	Size = UDim2.fromOffset(270, 130),
	Visible = false,
	ZIndex = 8,
	Parent = gui,
}, PURPLE, { noise = { 34, 16, 0.1 } })
scaled(toast)
local toastText = label({ Position = UDim2.fromOffset(10, 10), Size = UDim2.new(1, -20, 0, 50), Parent = toast }, 2.5)
local acceptButton = button({ Position = UDim2.fromOffset(10, 70), Size = UDim2.fromOffset(120, 48), Text = "✅ ДА", Parent = toast }, GREEN, 6)
local declineButton = button({ Position = UDim2.fromOffset(140, 70), Size = UDim2.fromOffset(120, 48), Text = "❌ НЕТ", Parent = toast }, RED, 6)
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
local shownPickaxe = 1

local function refreshHud()
	local coins = coinsValue.Value
	local level = player:GetAttribute("Level") or 1
	local levelCost = player:GetAttribute("LevelCost") or 1
	local rebirthCost = player:GetAttribute("RebirthCost")
	local pickaxe = player:GetAttribute("Pickaxe") or 1

	levelText.Text = "Уровень " .. level
	if coins >= levelCost then
		levelProgress.Text = "МОЖНО УЛУЧШИТЬ!"
		levelProgress.TextColor3 = GREEN_TEXT
	else
		levelProgress.Text = abbreviate(coins) .. " / " .. abbreviate(levelCost)
		levelProgress.TextColor3 = WHITE
	end
	TweenService:Create(levelFill, TweenInfo.new(0.2), { Size = UDim2.fromScale(math.clamp(coins / levelCost, 0, 1), 1) }):Play()
	statsLine.Text = "⛏ Урон " .. abbreviate(player:GetAttribute("Damage") or 1) .. "      💰 x" .. formatMultiplier(player:GetAttribute("CoinMultiplier") or 1)

	coinsText.Text = abbreviate(coins)
	rebirthsText.Text = tostring(rebirthsValue.Value)
	petsText.Text = #inventory.pets .. " / " .. INFO.maxPets

	upgradeButton.Text = "⬆ УЛУЧШИТЬ\n💰 " .. abbreviate(levelCost)
	upgradeButton.BackgroundColor3 = coins >= levelCost and GREEN or GRAY
	maxButton.BackgroundColor3 = coins >= levelCost and GREEN or GRAY

	rebirthBadge.Visible = rebirthCost ~= nil and coins >= rebirthCost
	local nextPick = INFO.pickaxes[pickaxe + 1]
	pickaxesBadge.Visible = nextPick ~= nil and coins >= nextPick.price

	if pickaxe ~= shownPickaxe then
		shownPickaxe = pickaxe
		clear(pickaxesTileIcon)
		pickaxeIcon(pickaxe, pickaxesTileIcon)
		clear(levelIconHolder)
		pickaxeIcon(pickaxe, levelIconHolder)
	end
end
pickaxeIcon(1, levelIconHolder)

-- Окна обновляем не чаще 4 раз в секунду
local updateQueued = false
local function queueUpdate()
	if updateQueued then
		return
	end
	updateQueued = true
	task.delay(0.25, function()
		updateQueued = false
		if openName then
			windows[openName].update()
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
local tint, applyWorldLook
do
	local atmosphere = Lighting:FindFirstChildOfClass("Atmosphere") or create("Atmosphere", { Parent = Lighting })
	local sky = Lighting:FindFirstChildOfClass("Sky")
	tint = create("ColorCorrectionEffect", { Name = "DestroyTint", Parent = Lighting })
	local grade = create("ColorCorrectionEffect", { Name = "DestroyGrade", Parent = Lighting })
	local bloom = Lighting:FindFirstChildOfClass("BloomEffect") or create("BloomEffect", { Parent = Lighting })
	bloom.Intensity = 0.3
	bloom.Size = 18
	bloom.Threshold = 2.2
	local sunRays = Lighting:FindFirstChildOfClass("SunRaysEffect") or create("SunRaysEffect", { Parent = Lighting })
	sunRays.Intensity = 0.03
	sunRays.Spread = 0.6
	Lighting.GlobalShadows = true
	Lighting.ShadowSoftness = 0.25
	Lighting.EnvironmentDiffuseScale = 1
	Lighting.EnvironmentSpecularScale = 1

	local WORLD_LOOK = {
		[1] = {
			clock = 14,
			brightness = 2,
			ambient = C(110, 110, 120),
			outdoor = C(140, 140, 150),
			density = 0.3,
			color = C(199, 220, 255),
			decay = C(110, 150, 200),
			haze = 1,
			stars = 0,
			contrast = 0.08,
			saturation = 0.05,
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

	function applyWorldLook()
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
end

------------------------------------------------------------------
-- ПИТОМЦЫ ХОДЯТ ЗА ИГРОКАМИ
------------------------------------------------------------------
do
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
end

------------------------------------------------------------------
-- КИРКА: удар по блоку, на который смотрит мышка/палец
------------------------------------------------------------------
do
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

	-- инвентарь спрятан, поэтому кирка всегда в руках
	local function equipPickaxe()
		local character = player.Character
		local humanoid = character and character:FindFirstChildOfClass("Humanoid")
		local backpack = player:FindFirstChildOfClass("Backpack")
		if humanoid and backpack and not character:FindFirstChildOfClass("Tool") then
			for _, tool in backpack:GetChildren() do
				if tool:IsA("Tool") and tool:GetAttribute("Pickaxe") then
					humanoid:EquipTool(tool)
					return
				end
			end
		end
	end
	task.spawn(function()
		while true do
			task.wait(0.5)
			equipPickaxe()
		end
	end)

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
	queueUpdate()
end)
rebirthsValue.Changed:Connect(function()
	refreshHud()
	queueUpdate()
end)

player.AttributeChanged:Connect(function(name)
	if name == "Combo" then
		onCombo()
		return
	end
	if name == "World" then
		applyWorldLook()
	end
	refreshHud()
	-- кирка или аддоны поменялись — перестраиваем окно кирок целиком
	if openName == "pickaxes" and (name == "Pickaxe" or string.sub(name, 1, 6) == "Addon_") then
		pickWindow.render()
	else
		queueUpdate()
	end
end)

inventoryRemote.OnClientEvent:Connect(function(data)
	inventory = data
	equippedSet = {}
	for _, id in inventory.equipped do
		equippedSet[id] = true
	end
	refreshHud()
	if openName == "pets" then
		petsWindow.render()
	elseif openName == "trade" and tradeState then
		tradeWindow.render()
	end
end)

pickaxesTile.Activated:Connect(function()
	toggleWindow("pickaxes")
end)
petsTile.Activated:Connect(function()
	toggleWindow("pets")
end)
eggsTile.Activated:Connect(function()
	toggleWindow("eggs")
end)
worldsTile.Activated:Connect(function()
	toggleWindow("worlds")
end)
tradeTile.Activated:Connect(function()
	toggleWindow("trade")
end)
rebirthTile.Activated:Connect(function()
	toggleWindow("rebirth")
end)

upgradeButton.Activated:Connect(function()
	shopRemote:FireServer("level")
end)
maxButton.Activated:Connect(function()
	shopRemote:FireServer("levelMax")
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
			eventText.Text = "МОНЕТЫ x" .. (workspace:GetAttribute("EventMultiplier") or 2) .. "\n" .. left .. " сек"
			eventBody.BackgroundColor3 = (math.floor(now * 2) % 2 == 0) and ORANGE or C(255, 170, 30)
		else
			local left = math.max(0, math.ceil((workspace:GetAttribute("NextEventAt") or now) - now))
			eventText.Text = string.format("через %d:%02d", left // 60, left % 60)
			eventBody.BackgroundColor3 = C(150, 90, 40)
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
