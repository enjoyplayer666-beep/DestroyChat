--[[
	DESTROY SIMULATOR — серверный скрипт
	Куда положить: ServerScriptService → Script (назови GameServer)

	Скрипт сам строит три блочных мира, спавнит блоки-руды, выдаёт кирки,
	считает монеты, питомцев, трейды, сохраняет прогресс и раз в несколько
	минут запускает «Золотую лихорадку».
	Все настройки — в таблицах CONFIG, PICKAXES, ADDONS, PETS и WORLDS ниже.

	ВНИМАНИЕ: скрипт удаляет стандартный Baseplate и SpawnLocation из шаблона,
	потому что строит свою карту.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local DataStoreService = game:GetService("DataStoreService")
local MarketplaceService = game:GetService("MarketplaceService")
local TweenService = game:GetService("TweenService")
local HttpService = game:GetService("HttpService")
local Debris = game:GetService("Debris")
local RunService = game:GetService("RunService")

local V = Vector3.new
local C = Color3.fromRGB
local NEON = Enum.Material.Neon
local SMOOTH = Enum.Material.SmoothPlastic

------------------------------------------------------------------
-- НАСТРОЙКИ (меняй смело)
------------------------------------------------------------------
local CONFIG = {
	TileSize = 6, -- размер одной плитки пола
	WorldTiles = 26, -- арена 26×26 плиток
	WorldSpacing = 1000, -- расстояние между мирами
	BlockSize = 5, -- размер ломаемого блока
	MaxBlocksPerWorld = 28, -- сколько блоков одновременно в каждом мире
	SpawnInterval = 0.4, -- как часто появляется новый блок (сек)
	ClickDistance = 40, -- с какого расстояния можно бить блок
	HitCooldown = 0.1, -- защита от автокликера (сек между ударами)
	CritChance = 0.1, -- базовый шанс крита (0.1 = 10%)
	CritMultiplier = 3, -- во сколько раз крит сильнее
	ComboWindow = 1.5, -- сколько секунд держится комбо между ударами
	ComboMax = 50, -- на комбо 50 и выше монеты x2
	RebirthBonus = 0.5, -- +50% монет за каждый ребёрт
	MaxPets = 60, -- размер инвентаря питомцев
	TradeMaxPets = 8, -- сколько питомцев можно положить в один трейд
	TradeCountdown = 3, -- отсчёт перед обменом (сек)
	EventEvery = 180, -- «Золотая лихорадка» каждые N секунд
	EventDuration = 30, -- длительность лихорадки (сек)
	EventCoinMultiplier = 2, -- множитель монет во время лихорадки
	EventRareBoost = 4, -- во сколько раз чаще редкие блоки во время лихорадки
	AutosaveEvery = 60, -- автосохранение (сек)
	DataStoreName = "DestroySim_v2",
}

------------------------------------------------------------------
-- КИРКИ. damage — множитель урона, addonCap — до какого уровня
-- можно прокачать аддоны с этой киркой.
------------------------------------------------------------------
local PICKAXES = {
	{ name = "Деревянная кирка", damage = 1, price = 0, addonCap = 1, color = C(160, 125, 75) },
	{ name = "Каменная кирка", damage = 2, price = 500, addonCap = 2, color = C(130, 130, 130) },
	{ name = "Железная кирка", damage = 4, price = 7500, addonCap = 3, color = C(225, 225, 225) },
	{ name = "Золотая кирка", damage = 8, price = 60000, addonCap = 4, color = C(255, 215, 60) },
	{ name = "Алмазная кирка", damage = 16, price = 750000, addonCap = 6, color = C(90, 235, 240) },
	{ name = "Незеритовая кирка", damage = 40, price = 15000000, addonCap = 8, color = C(75, 65, 70) },
	{ name = "Кирка Края", damage = 120, price = 500000000, addonCap = 10, color = C(200, 90, 255), neon = true },
}

------------------------------------------------------------------
-- АДДОНЫ ДЛЯ КИРОК. Цена уровня = baseCost * growth ^ текущий_уровень
------------------------------------------------------------------
local ADDONS = {
	{ id = "efficiency", name = "Эффективность", desc = "+20% урона за уровень", baseCost = 300, growth = 3 },
	{ id = "fortune", name = "Удача", desc = "+25% изумрудов за уровень", baseCost = 500, growth = 3 },
	{ id = "sharpness", name = "Меткость", desc = "+3% шанс крита за уровень", baseCost = 400, growth = 3 },
	{ id = "blast", name = "Взрыв", desc = "+8% шанс взрыва, задевает блоки рядом", baseCost = 2000, growth = 4 },
	{ id = "auto", name = "Автокопка", desc = "Кирка сама бьёт ближайший блок", baseCost = 1500, growth = 4 },
}
------------------------------------------------------------------
-- УЛУЧШЕНИЯ У КРИПЕРА: слоты питомцев (сколько можно надеть).
-- Цена — сколько изумрудов стоит открыть этот слот.
------------------------------------------------------------------
local BASE_PET_SLOTS = 1
local MAX_PET_SLOTS = 5
local PET_SLOT_PRICES = { [2] = 2500, [3] = 40000, [4] = 750000, [5] = 15000000 }

------------------------------------------------------------------
-- АУРЫ У ЭНДЕРМЕНА: пламя вокруг игрока, boost — множитель изумрудов
------------------------------------------------------------------
local AURAS = {
	{ id = "flame", name = "Пламя", boost = 1.2, price = 5000, color = C(255, 140, 40), secondary = C(255, 220, 80) },
	{ id = "soul", name = "Пламя душ", boost = 1.5, price = 100000, color = C(70, 200, 255), secondary = C(190, 240, 255) },
	{ id = "emerald", name = "Изумрудное пламя", boost = 2, price = 2000000, color = C(60, 230, 90), secondary = C(200, 255, 180) },
	{ id = "ender", name = "Пламя Края", boost = 3, price = 50000000, color = C(190, 90, 255), secondary = C(240, 190, 255) },
	{ id = "dragon", name = "Дыхание дракона", boost = 5, price = 1000000000, color = C(255, 60, 200), secondary = C(255, 200, 240) },
}
local AURA_BY_ID = {}
for _, aura in AURAS do
	AURA_BY_ID[aura.id] = aura
end

------------------------------------------------------------------
-- ЗЕЛЬЯ У ТОРГОВЦА: цена = max(base, цена_уровня_кирки * mult)
------------------------------------------------------------------
local POTIONS = {
	{ id = "wealth", name = "Зелье богатства", desc = "x2 изумрудов на 10 минут", minutes = 10, base = 1500, mult = 4, color = C(80, 230, 100) },
	{ id = "power", name = "Зелье силы", desc = "x2 урона на 10 минут", minutes = 10, base = 1500, mult = 4, color = C(235, 60, 60) },
	{ id = "speed", name = "Зелье скорости", desc = "Быстрый бег на 10 минут", minutes = 10, base = 300, mult = 1, color = C(90, 170, 255) },
}
local POTION_BY_ID = {}
for _, potion in POTIONS do
	POTION_BY_ID[potion.id] = potion
end

local ADDON_BY_ID = {}
for _, addon in ADDONS do
	ADDON_BY_ID[addon.id] = addon
end

------------------------------------------------------------------
-- ПИТОМЦЫ. bonus = +монет (0.1 = +10%). parts — из каких кубиков собран.
-- Каждый кубик: { размер, смещение, цвет, [материал] }. Перед питомца смотрит в -Z.
------------------------------------------------------------------
local RARITIES = {
	["Обычный"] = { color = C(200, 200, 200), order = 1 },
	["Редкий"] = { color = C(80, 160, 255), order = 2 },
	["Эпический"] = { color = C(180, 90, 255), order = 3 },
	["Легендарный"] = { color = C(255, 200, 40), order = 4 },
	["Мифический"] = { color = C(255, 70, 120), order = 5 },
}

local BLACK = C(25, 25, 25)
local EYE_WHITE = C(240, 240, 240)

-- Собирает мини-моба из кубиков.
-- box — один кубик, pair — два зеркальных кубика (слева и справа).
-- Первый кубик — «тело», вокруг него питомец и двигается.
local function mob(rarity, bonus, fly, build)
	local parts = {}
	local function box(size, position, color, material)
		table.insert(parts, { size, position, color, material })
	end
	local function pair(size, position, color, material)
		box(size, position, color, material)
		box(size, V(-position.X, position.Y, position.Z), color, material)
	end
	build(box, pair)
	return { rarity = rarity, bonus = bonus, fly = fly, parts = parts }
end

local PETS = {
	["Курица"] = mob("Обычный", 0.1, false, function(box, pair)
		local white, orange = C(245, 245, 245), C(235, 165, 40)
		box(V(1.2, 1.0, 1.5), V(0, 0, 0), white)
		box(V(0.9, 0.6, 0.3), V(0, 0.25, 0.85), C(230, 230, 230))
		box(V(0.8, 1.0, 0.6), V(0, 0.8, -0.85), white)
		box(V(0.8, 0.3, 0.3), V(0, 0.8, -1.3), orange)
		box(V(0.6, 0.15, 0.3), V(0, 0.6, -1.28), C(205, 135, 30))
		box(V(0.4, 0.35, 0.15), V(0, 0.42, -1.22), C(200, 35, 35))
		pair(V(0.15, 0.15, 0.05), V(0.28, 1.05, -1.175), BLACK)
		pair(V(0.15, 0.7, 1.0), V(0.68, 0.05, 0.05), C(230, 230, 230))
		pair(V(0.16, 0.25, 0.4), V(0.68, -0.2, 0.4), C(210, 210, 210))
		pair(V(0.15, 0.6, 0.15), V(0.3, -0.8, 0.1), orange)
		pair(V(0.4, 0.1, 0.5), V(0.3, -1.1, -0.05), orange)
	end),
	["Свинка"] = mob("Обычный", 0.15, false, function(box, pair)
		local pink, dark = C(240, 165, 165), C(225, 140, 145)
		box(V(1.4, 1.1, 2.0), V(0, 0, 0), pink)
		box(V(0.5, 0.05, 0.4), V(0.2, 0.555, 0.3), dark)
		box(V(0.05, 0.4, 0.5), V(0.705, 0.1, -0.3), dark)
		box(V(1.2, 1.1, 1.0), V(0, 0.25, -1.45), pink)
		box(V(0.6, 0.45, 0.15), V(0, 0.05, -2.02), C(230, 135, 150))
		pair(V(0.12, 0.15, 0.05), V(0.15, 0.05, -2.1), C(150, 70, 80))
		pair(V(0.28, 0.18, 0.05), V(0.38, 0.42, -1.975), EYE_WHITE)
		pair(V(0.14, 0.18, 0.06), V(0.31, 0.42, -1.98), BLACK)
		pair(V(0.3, 0.25, 0.15), V(0.45, 0.85, -1.3), dark)
		pair(V(0.4, 0.6, 0.4), V(0.45, -0.8, 0.6), pink)
		pair(V(0.4, 0.6, 0.4), V(0.45, -0.8, -0.6), pink)
		pair(V(0.42, 0.15, 0.42), V(0.45, -1.05, 0.6), C(200, 120, 125))
		pair(V(0.42, 0.15, 0.42), V(0.45, -1.05, -0.6), C(200, 120, 125))
		box(V(0.15, 0.15, 0.3), V(0, 0.3, 1.1), dark)
	end),
	["Овечка"] = mob("Редкий", 0.3, false, function(box, pair)
		local wool, face, fabric = C(240, 240, 240), C(215, 180, 160), Enum.Material.Fabric
		box(V(1.6, 1.4, 2.1), V(0, 0, 0), wool, fabric)
		box(V(1.3, 0.2, 1.7), V(0, 0.75, 0), C(250, 250, 250), fabric)
		pair(V(0.15, 1.0, 1.6), V(0.85, 0, 0), C(228, 228, 228), fabric)
		box(V(0.9, 0.9, 0.9), V(0, 0.45, -1.4), face)
		box(V(1.0, 0.35, 0.95), V(0, 0.95, -1.38), wool, fabric)
		pair(V(0.25, 0.15, 0.05), V(0.26, 0.5, -1.875), EYE_WHITE)
		pair(V(0.12, 0.15, 0.06), V(0.2, 0.5, -1.88), BLACK)
		box(V(0.3, 0.15, 0.05), V(0, 0.25, -1.875), C(240, 180, 180))
		pair(V(0.35, 0.18, 0.25), V(0.55, 0.6, -1.35), face)
		pair(V(0.35, 0.75, 0.35), V(0.45, -0.95, 0.65), face)
		pair(V(0.35, 0.75, 0.35), V(0.45, -0.95, -0.65), face)
		pair(V(0.37, 0.15, 0.37), V(0.45, -1.27, 0.65), C(160, 130, 115))
		pair(V(0.37, 0.15, 0.37), V(0.45, -1.27, -0.65), C(160, 130, 115))
	end),
	["Волк"] = mob("Эпический", 0.6, false, function(box, pair)
		local fur, muzzle, paw = C(215, 215, 215), C(185, 178, 170), C(245, 245, 245)
		box(V(1.0, 1.0, 1.8), V(0, 0, 0.1), fur)
		box(V(1.35, 1.3, 1.0), V(0, 0.15, -0.65), fur)
		box(V(1.0, 0.9, 0.8), V(0, 0.4, -1.45), fur)
		box(V(0.5, 0.4, 0.45), V(0, 0.2, -2.05), muzzle)
		box(V(0.22, 0.15, 0.05), V(0, 0.35, -2.28), BLACK)
		pair(V(0.2, 0.12, 0.05), V(0.28, 0.55, -1.875), EYE_WHITE)
		pair(V(0.1, 0.12, 0.06), V(0.23, 0.55, -1.88), BLACK)
		pair(V(0.25, 0.35, 0.15), V(0.3, 1.0, -1.35), fur)
		pair(V(0.12, 0.2, 0.05), V(0.3, 0.98, -1.43), C(200, 150, 150))
		box(V(1.38, 0.22, 1.02), V(0, -0.3, -0.65), C(200, 40, 40))
		box(V(0.18, 0.22, 0.05), V(0, -0.5, -1.17), C(230, 200, 60))
		box(V(0.3, 0.3, 0.9), V(0, 0.3, 1.35), fur)
		box(V(0.32, 0.32, 0.25), V(0, 0.3, 1.82), paw)
		pair(V(0.3, 0.75, 0.3), V(0.3, -0.85, 0.65), fur)
		pair(V(0.3, 0.75, 0.3), V(0.3, -0.85, -0.45), fur)
		pair(V(0.32, 0.12, 0.36), V(0.3, -1.2, 0.63), paw)
		pair(V(0.32, 0.12, 0.36), V(0.3, -1.2, -0.47), paw)
	end),
	["Аксолотль"] = mob("Легендарный", 1.5, false, function(box, pair)
		local pink, gill = C(250, 170, 200), C(220, 60, 140)
		box(V(0.9, 0.6, 1.6), V(0, 0, 0), pink)
		box(V(1.1, 0.8, 0.9), V(0, 0.1, -1.15), pink)
		pair(V(0.5, 0.12, 0.12), V(0.75, 0.45, -1.0), gill)
		pair(V(0.5, 0.12, 0.12), V(0.75, 0.2, -1.0), gill)
		pair(V(0.5, 0.12, 0.12), V(0.75, -0.05, -1.0), gill)
		pair(V(0.12, 0.4, 0.12), V(0.35, 0.65, -1.0), gill)
		pair(V(0.15, 0.15, 0.05), V(0.38, 0.2, -1.625), BLACK)
		box(V(0.45, 0.06, 0.05), V(0, -0.05, -1.625), C(200, 90, 130))
		pair(V(0.2, 0.05, 0.2), V(0.2, 0.305, 0.2), C(230, 140, 175))
		box(V(0.12, 0.5, 1.0), V(0, 0.05, 1.3), C(240, 150, 190))
		box(V(0.08, 0.2, 0.8), V(0, 0.35, 1.3), gill)
		pair(V(0.25, 0.3, 0.25), V(0.45, -0.4, 0.5), C(240, 150, 190))
		pair(V(0.25, 0.3, 0.25), V(0.45, -0.4, -0.5), C(240, 150, 190))
	end),
	["Магмовый куб"] = mob("Обычный", 0.8, false, function(box, pair)
		local crust, glow = C(70, 25, 20), C(255, 120, 20)
		box(V(1.4, 1.4, 1.4), V(0, 0, 0), glow, NEON)
		for _, y in { 0.65, 0.22, -0.22, -0.65 } do
			box(V(1.7, 0.32, 1.7), V(0, y, 0), crust)
		end
		pair(V(0.25, 0.05, 0.25), V(0.4, 0.815, 0.3), C(120, 40, 30))
		pair(V(0.35, 0.18, 0.05), V(0.4, 0.45, -0.875), C(255, 220, 60), NEON)
		pair(V(0.15, 0.18, 0.06), V(0.33, 0.45, -0.88), C(255, 80, 20), NEON)
	end),
	["Страйдер"] = mob("Редкий", 1.2, false, function(box, pair)
		local hair = C(80, 35, 45)
		box(V(1.6, 1.4, 1.6), V(0, 0.5, 0), C(175, 50, 55))
		box(V(1.62, 0.3, 1.62), V(0, 0.05, 0), C(140, 35, 45))
		for _, p in { V(-0.55, 1.55, 0.2), V(-0.2, 1.6, -0.3), V(0.15, 1.5, 0.4), V(0.5, 1.55, -0.1), V(0, 1.6, 0.1), V(-0.4, 1.5, -0.6) } do
			box(V(0.12, 0.7, 0.12), p, hair)
		end
		pair(V(0.32, 0.16, 0.05), V(0.38, 0.75, -0.825), C(240, 230, 200))
		pair(V(0.16, 0.16, 0.06), V(0.3, 0.75, -0.83), BLACK)
		box(V(1.0, 0.1, 0.05), V(0, 0.35, -0.825), C(60, 20, 25))
		pair(V(0.35, 1.3, 0.35), V(0.45, -0.85, 0), C(110, 85, 95))
		pair(V(0.42, 0.15, 0.5), V(0.45, -1.5, -0.05), C(80, 60, 70))
	end),
	["Ифрит"] = mob("Эпический", 2, true, function(box, pair)
		local rod = C(255, 150, 30)
		box(V(1.0, 1.0, 1.0), V(0, 0, 0), C(255, 195, 60))
		box(V(1.05, 0.15, 1.05), V(0, 0.45, 0), C(230, 160, 40))
		pair(V(0.3, 0.1, 0.05), V(0.25, 0.22, -0.525), C(180, 110, 30))
		pair(V(0.25, 0.15, 0.05), V(0.25, 0.05, -0.525), C(60, 30, 10))
		box(V(0.4, 0.1, 0.05), V(0, -0.25, -0.525), C(150, 80, 20))
		for _, p in { V(0.85, -0.5, 0), V(-0.85, -0.5, 0), V(0, -0.5, 0.85), V(0, -0.5, -0.85) } do
			box(V(0.22, 0.8, 0.22), p, rod, NEON)
		end
		for _, p in { V(0.6, -1.35, 0.6), V(-0.6, -1.35, 0.6), V(0.6, -1.35, -0.6), V(-0.6, -1.35, -0.6) } do
			box(V(0.22, 0.8, 0.22), p, rod, NEON)
		end
	end),
	["Гаст"] = mob("Легендарный", 5, true, function(box, pair)
		local white = C(240, 240, 240)
		box(V(2.4, 2.4, 2.4), V(0, 0, 0), white)
		box(V(2.42, 0.6, 2.42), V(0, -0.9, 0), C(225, 225, 225))
		pair(V(0.5, 0.15, 0.05), V(0.55, 0.35, -1.225), C(70, 70, 70))
		pair(V(0.15, 0.5, 0.05), V(0.7, -0.05, -1.225), C(160, 170, 190))
		box(V(0.7, 0.4, 0.05), V(0, -0.5, -1.225), C(70, 70, 70))
		local lengths = { 1.2, 1.6, 1.0, 1.4, 1.8, 1.3, 1.1, 1.5, 1.2 }
		local index = 0
		for _, x in { -0.8, 0, 0.8 } do
			for _, z in { -0.7, 0, 0.7 } do
				index += 1
				local length = lengths[index]
				box(V(0.3, length, 0.3), V(x, -1.2 - length / 2, z), white)
			end
		end
	end),
	["Эндермит"] = mob("Обычный", 4, false, function(box, pair)
		local shell = C(50, 40, 60)
		box(V(0.7, 0.55, 0.6), V(0, 0, 0), shell)
		box(V(0.6, 0.5, 0.55), V(0, -0.02, -0.58), shell)
		box(V(0.6, 0.5, 0.55), V(0, -0.02, 0.58), shell)
		box(V(0.4, 0.35, 0.4), V(0, -0.08, 1.05), shell)
		box(V(0.25, 0.2, 0.3), V(0, -0.12, 1.38), shell)
		pair(V(0.15, 0.05, 0.15), V(0.15, 0.28, 0), C(80, 60, 95))
		pair(V(0.12, 0.12, 0.05), V(0.18, 0.08, -0.88), C(220, 120, 255), NEON)
		for _, z in { -0.4, 0, 0.4 } do
			pair(V(0.4, 0.08, 0.08), V(0.45, -0.22, z), C(40, 30, 50))
		end
	end),
	["Шалкер"] = mob("Редкий", 6, false, function(box, pair)
		box(V(0.9, 0.9, 0.9), V(0, 0, 0), C(225, 235, 130))
		pair(V(0.15, 0.15, 0.05), V(0.2, 0.05, -0.475), BLACK)
		box(V(0.3, 0.08, 0.05), V(0, -0.2, -0.475), C(120, 130, 60))
		box(V(1.7, 0.8, 1.7), V(0, 0.7, 0.2), C(155, 105, 165))
		box(V(1.72, 0.08, 1.72), V(0, 0.3, 0.2), C(110, 70, 120))
		box(V(1.7, 0.6, 1.7), V(0, -0.6, 0.2), C(140, 95, 150))
		box(V(1.2, 0.05, 1.2), V(0, 1.105, 0.2), C(175, 125, 185))
	end),
	["Эндермен"] = mob("Эпический", 10, false, function(box, pair)
		local black = C(20, 20, 25)
		box(V(0.9, 1.3, 0.5), V(0, 0, 0), black)
		box(V(1.0, 1.0, 1.0), V(0, 1.15, 0), black)
		box(V(1.0, 0.25, 1.0), V(0, 0.52, -0.05), C(30, 30, 36))
		pair(V(0.35, 0.12, 0.05), V(0.25, 1.05, -0.525), C(225, 130, 255), NEON)
		pair(V(0.12, 0.12, 0.06), V(0.18, 1.05, -0.53), C(180, 60, 230), NEON)
		pair(V(0.2, 2.0, 0.2), V(0.55, -0.35, 0), black)
		pair(V(0.22, 1.8, 0.22), V(0.22, -1.55, 0), black)
		-- держит блок травы
		box(V(0.5, 0.5, 0.5), V(0, -0.9, -0.45), C(134, 96, 67))
		box(V(0.52, 0.12, 0.52), V(0, -0.68, -0.45), C(95, 159, 53))
	end),
	["Дракон Края"] = mob("Мифический", 30, true, function(box, pair)
		local dark, bone = C(28, 22, 34), C(130, 125, 140)
		box(V(1.2, 1.0, 2.4), V(0, 0, 0), dark)
		box(V(1.0, 0.1, 2.0), V(0, -0.52, 0), C(45, 38, 52))
		box(V(0.6, 0.6, 0.9), V(0, 0.3, -1.55), dark)
		box(V(1.0, 0.8, 1.0), V(0, 0.5, -2.35), dark)
		box(V(0.7, 0.4, 0.7), V(0, 0.35, -3.1), dark)
		box(V(0.65, 0.15, 0.65), V(0, 0.08, -3.05), C(40, 32, 48))
		pair(V(0.1, 0.1, 0.05), V(0.2, 0.5, -3.475), C(70, 60, 80))
		pair(V(0.22, 0.1, 0.05), V(0.3, 0.7, -2.875), C(220, 120, 255), NEON)
		pair(V(0.12, 0.4, 0.12), V(0.3, 1.05, -2.1), bone)
		for _, z in { -1.5, -0.8, 0, 0.8 } do
			box(V(0.15, 0.3, 0.3), V(0, z == -1.5 and 0.7 or 0.65, z), bone)
		end
		pair(V(2.4, 0.15, 0.2), V(1.8, 0.5, -0.5), dark)
		pair(V(2.4, 0.06, 1.4), V(1.8, 0.48, 0.25), C(70, 60, 85))
		box(V(0.45, 0.45, 1.6), V(0, 0.1, 2.0), dark)
		box(V(0.35, 0.35, 1.2), V(0, 0.05, 3.3), dark)
		box(V(0.1, 0.25, 0.25), V(0, 0.4, 2.0), bone)
		box(V(0.1, 0.25, 0.25), V(0, 0.32, 3.3), bone)
		pair(V(0.3, 0.6, 0.3), V(0.45, -0.75, -0.6), dark)
		pair(V(0.3, 0.6, 0.3), V(0.45, -0.75, 0.7), dark)
	end),
}

------------------------------------------------------------------
-- МИРЫ И БЛОКИ.
-- look: "noise" (обычный блок), "ore" (руда с вкраплениями), "grass",
-- "log" (бревно), "glow" (светящийся). weight — как часто появляется.
------------------------------------------------------------------
local STONE = C(125, 125, 125)
local NETHERRACK = C(111, 54, 53)

local WORLDS = {
	{
		id = 1,
		name = "Луга",
		price = 0,
		theme = "overworld",
		accent = C(110, 220, 90),
		eventBlock = 7, -- какой блок сыпется во время лихорадки
		blocks = {
			{ name = "Трава", look = "grass", color = C(134, 96, 67), top = C(95, 159, 53), hp = 5, reward = 3, weight = 30 },
			{ name = "Земля", look = "noise", color = C(134, 96, 67), hp = 4, reward = 2, weight = 25 },
			{ name = "Бревно", look = "log", color = C(102, 81, 50), top = C(170, 140, 85), hp = 10, reward = 6, weight = 18 },
			{ name = "Камень", look = "noise", color = STONE, hp = 20, reward = 12, weight = 15 },
			{ name = "Угольная руда", look = "ore", color = STONE, spot = C(35, 35, 35), hp = 35, reward = 25, weight = 10 },
			{ name = "Железная руда", look = "ore", color = STONE, spot = C(216, 175, 147), hp = 80, reward = 60, weight = 7 },
			{ name = "Золотая руда", look = "ore", color = STONE, spot = C(252, 238, 75), hp = 250, reward = 300, weight = 3, rare = true },
			{
				name = "Алмазная руда",
				look = "ore",
				color = STONE,
				spot = C(93, 236, 245),
				hp = 1500,
				reward = 2500,
				weight = 1,
				rare = true,
				announce = true,
			},
		},
		egg = {
			name = "Яйцо Лугов",
			price = 1000,
			color = C(240, 230, 200),
			spot = C(120, 190, 80),
			pets = {
				{ kind = "Курица", weight = 45 },
				{ kind = "Свинка", weight = 30 },
				{ kind = "Овечка", weight = 17 },
				{ kind = "Волк", weight = 7 },
				{ kind = "Аксолотль", weight = 1 },
			},
		},
	},
	{
		id = 2,
		name = "Нижний мир",
		price = 50000,
		theme = "nether",
		accent = C(255, 100, 50),
		eventBlock = 5,
		blocks = {
			{ name = "Адский камень", look = "noise", color = NETHERRACK, hp = 150, reward = 300, weight = 35 },
			{ name = "Песок душ", look = "ore", color = C(84, 64, 51), spot = C(60, 45, 36), hp = 300, reward = 650, weight = 25 },
			{ name = "Кварцевая руда", look = "ore", color = NETHERRACK, spot = C(235, 225, 215), hp = 700, reward = 1600, weight = 18 },
			{ name = "Светокамень", look = "glow", color = C(255, 205, 120), spot = C(190, 130, 60), hp = 1500, reward = 3500, weight = 12 },
			{ name = "Адское золото", look = "ore", color = NETHERRACK, spot = C(252, 220, 60), hp = 6000, reward = 15000, weight = 6, rare = true },
			{
				name = "Древние обломки",
				look = "ore",
				color = C(95, 70, 65),
				spot = C(140, 110, 100),
				hp = 40000,
				reward = 120000,
				weight = 1,
				rare = true,
				announce = true,
			},
		},
		egg = {
			name = "Огненное яйцо",
			price = 100000,
			color = C(90, 30, 25),
			spot = C(255, 130, 30),
			pets = {
				{ kind = "Магмовый куб", weight = 50 },
				{ kind = "Страйдер", weight = 33 },
				{ kind = "Ифрит", weight = 15 },
				{ kind = "Гаст", weight = 2 },
			},
		},
	},
	{
		id = 3,
		name = "Край",
		price = 5000000,
		theme = "ender",
		accent = C(200, 120, 255),
		eventBlock = 4,
		blocks = {
			{ name = "Камень Края", look = "noise", color = C(219, 222, 158), hp = 2500, reward = 20000, weight = 35 },
			{ name = "Обсидиан", look = "ore", color = C(20, 16, 32), spot = C(60, 40, 90), hp = 6000, reward = 50000, weight = 28 },
			{ name = "Пурпур", look = "ore", color = C(169, 125, 169), spot = C(140, 100, 140), hp = 12000, reward = 110000, weight = 20 },
			{ name = "Кристалл Края", look = "glow", color = C(255, 140, 255), spot = C(140, 60, 160), hp = 50000, reward = 500000, weight = 6, rare = true },
			{
				name = "Яйцо дракона",
				look = "ore",
				color = C(12, 9, 15),
				spot = C(140, 70, 200),
				hp = 400000,
				reward = 5000000,
				weight = 1,
				rare = true,
				announce = true,
				size = 6,
			},
		},
		egg = {
			name = "Яйцо Края",
			price = 10000000,
			color = C(30, 20, 40),
			spot = C(190, 90, 255),
			pets = {
				{ kind = "Эндермит", weight = 50 },
				{ kind = "Шалкер", weight = 33 },
				{ kind = "Эндермен", weight = 15 },
				{ kind = "Дракон Края", weight = 2 },
			},
		},
	},
}

local THEMES = {
	overworld = { floor = C(84, 140, 50), column = C(134, 96, 67), columnTop = C(95, 155, 55) },
	nether = { floor = NETHERRACK, floorAlt = C(84, 64, 51), column = C(100, 45, 45) },
	ender = { floor = C(219, 222, 158), column = C(205, 208, 145) },
}

local COIN_STAT = "Изумруды"
-- значок изумруда внутри текста (цветной ромбик)
local EM = '<font color="#4CF07A">◆</font>'
local REBIRTH_STAT = "Ребёрты"

local GOLD = C(255, 200, 40)
local RED = C(255, 80, 80)
local GREEN = C(90, 230, 110)
local WHITE = Color3.new(1, 1, 1)

------------------------------------------------------------------
-- ФОРМУЛЫ
------------------------------------------------------------------
local function levelCost(level)
	return math.floor(15 * 1.22 ^ (level - 1))
end

local function rebirthCost(rebirths)
	return 10000 * (rebirths + 1) ^ 2
end

local function addonCost(addon, level)
	return math.floor(addon.baseCost * addon.growth ^ level)
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

local function makeRemote(className, name)
	local remote = Instance.new(className)
	remote.Name = name
	remote.Parent = remotes
	return remote
end

local infoRemote = makeRemote("RemoteFunction", "GetInfo")
local shopRemote = makeRemote("RemoteEvent", "Shop")
local hitRemote = makeRemote("RemoteEvent", "Hit")
local announceRemote = makeRemote("RemoteEvent", "Announce")
local petRemote = makeRemote("RemoteEvent", "Pets")
local inventoryRemote = makeRemote("RemoteEvent", "Inventory")
local openEggRemote = makeRemote("RemoteEvent", "OpenEgg")
local uiRemote = makeRemote("RemoteEvent", "UI")
local eggResultRemote = makeRemote("RemoteEvent", "EggOpened")
local worldRemote = makeRemote("RemoteEvent", "World")
local tradeRemote = makeRemote("RemoteEvent", "Trade")
local adminRemote = makeRemote("RemoteEvent", "Admin")

local function announce(text, color, target)
	if target then
		announceRemote:FireClient(target, text, color or WHITE)
	else
		announceRemote:FireAllClients(text, color or WHITE)
	end
end

------------------------------------------------------------------
-- ПОСТРОЕНИЕ
------------------------------------------------------------------
local function makePart(props)
	local part = Instance.new("Part")
	part.Anchored = true
	part.Material = SMOOTH
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

-- Немного меняет яркость цвета, чтобы плитки выглядели «пиксельно»
local function shade(color, amount)
	local k = 1 + (math.random() - 0.5) * amount
	return Color3.new(math.clamp(color.R * k, 0, 1), math.clamp(color.G * k, 0, 1), math.clamp(color.B * k, 0, 1))
end

-- Грани куба (без нижней): n — наружу, u и v — вдоль грани
local FACES = {
	{ n = V(1, 0, 0), u = V(0, 0, 1), v = V(0, 1, 0) },
	{ n = V(-1, 0, 0), u = V(0, 0, 1), v = V(0, 1, 0) },
	{ n = V(0, 0, 1), u = V(1, 0, 0), v = V(0, 1, 0) },
	{ n = V(0, 0, -1), u = V(1, 0, 0), v = V(0, 1, 0) },
	{ n = V(0, 1, 0), u = V(1, 0, 0), v = V(0, 0, 1) },
}
for _, face in FACES do
	face.nAbs = V(math.abs(face.n.X), math.abs(face.n.Y), math.abs(face.n.Z))
end

-- Пиксельные пятна на гранях куба (как текстура руды)
local function addSpots(parent, core, colors, perFace, options)
	options = options or {}
	local size = core.Size
	for _, face in FACES do
		local isSide = face.n.Y == 0
		if (options.sidesOnly and not isSide) or (options.topOnly and isSide) then
			continue
		end
		local su = math.abs(size:Dot(face.u))
		local sv = math.abs(size:Dot(face.v))
		local sn = math.abs(size:Dot(face.n))
		local pu, pv = su / 8, sv / 8
		for _ = 1, perFace do
			local wpx = options.width or math.random(1, 2)
			local hpx = options.height and math.random(options.height[1], options.height[2]) or math.random(1, 2)
			local a = (math.random(0, 8 - wpx) + wpx / 2 - 4) * pu
			local row = options.row or math.random(0, (options.maxRow or 8) - hpx)
			local b = (row + hpx / 2 - 4) * pv
			makePart({
				Name = "Spot",
				Size = face.nAbs * 0.1 + face.u * (wpx * pu) + face.v * (hpx * pv),
				CFrame = core.CFrame + face.n * (sn / 2 + 0.03) + face.u * a + face.v * b,
				Color = colors[math.random(1, #colors)],
				Material = options.material or SMOOTH,
				CanCollide = false,
				CanTouch = false,
				Parent = parent,
			})
		end
	end
end

local DARK = Color3.new(0, 0, 0)

-- Строит ломаемый блок в стиле «пиксельного куба»
local function buildBlockModel(def, position)
	local s = def.size or CONFIG.BlockSize
	local px = s / 8
	local model = Instance.new("Model")
	model.Name = def.name

	local core = makePart({
		Name = "Core",
		Size = V(s, s, s),
		Position = position,
		Color = def.color,
		Material = def.look == "glow" and NEON or SMOOTH,
		Parent = model,
	})
	model.PrimaryPart = core

	local darker = def.color:Lerp(DARK, 0.15)
	local lighter = def.color:Lerp(WHITE, 0.1)

	if def.look == "grass" then
		makePart({
			Name = "Top",
			Size = V(s + 0.06, px * 1.5, s + 0.06),
			Position = position + V(0, s / 2 - px * 0.75 + 0.03, 0),
			Color = def.top,
			CanTouch = false,
			Parent = model,
		})
		-- зелёные «подтёки» по бокам
		addSpots(model, core, { def.top, def.top:Lerp(DARK, 0.15) }, 3, { sidesOnly = true, width = 1, row = 5, height = { 1, 2 } })
		addSpots(model, core, { darker, lighter }, 3, { sidesOnly = true, maxRow = 5 })
	elseif def.look == "log" then
		addSpots(model, core, { darker, def.color:Lerp(DARK, 0.3) }, 3, { sidesOnly = true, width = 1, height = { 3, 6 } })
		makePart({
			Name = "Rings",
			Size = V(s - px * 2, 0.1, s - px * 2),
			Position = position + V(0, s / 2 + 0.03, 0),
			Color = def.top,
			CanCollide = false,
			CanTouch = false,
			Parent = model,
		})
		makePart({
			Name = "Rings",
			Size = V(s - px * 4, 0.12, s - px * 4),
			Position = position + V(0, s / 2 + 0.04, 0),
			Color = def.top:Lerp(DARK, 0.2),
			CanCollide = false,
			CanTouch = false,
			Parent = model,
		})
	elseif def.look == "ore" then
		addSpots(model, core, { darker, lighter }, 2)
		addSpots(model, core, { def.spot, def.spot:Lerp(DARK, 0.2) }, 4)
	elseif def.look == "glow" then
		addSpots(model, core, { def.spot, def.spot:Lerp(DARK, 0.2) }, 4)
	else
		addSpots(model, core, { darker, lighter }, 5)
	end

	if def.rare then
		local light = Instance.new("PointLight")
		light.Color = def.spot or def.color
		light.Brightness = 2
		light.Range = 16
		light.Parent = core

		local sparkles = Instance.new("Sparkles")
		sparkles.SparkleColor = def.spot or def.color
		sparkles.Parent = core
	end

	return model, core
end

------------------------------------------------------------------
-- МИРЫ
------------------------------------------------------------------
-- Убираем стандартный пол и спавн из шаблона Baseplate — у нас своя карта
for _, child in workspace:GetChildren() do
	if (child.Name == "Baseplate" and child:IsA("BasePart")) or child:IsA("SpawnLocation") then
		child:Destroy()
	end
end

local worldsFolder = Instance.new("Folder")
worldsFolder.Name = "Worlds"
worldsFolder.Parent = workspace

local blocksFolder = Instance.new("Folder")
blocksFolder.Name = "Destructibles"
blocksFolder.Parent = workspace

local effectsFolder = Instance.new("Folder")
effectsFolder.Name = "Effects"
effectsFolder.Parent = workspace

local function worldOrigin(id)
	return V((id - 1) * CONFIG.WorldSpacing, 0, 0)
end

local SPAWN_Z = -(CONFIG.WorldTiles / 2 - 1.5) * CONFIG.TileSize

local HUB_SPAWN = V(0, 0.5, -140)
local spawnSpots = {} -- [worldId] = { { key, x, z } } — где могут появляться блоки

local function spawnCFrame(id)
	local position = id == 1 and HUB_SPAWN + V(0, 3.5, 0) or worldOrigin(id) + V(0, 4, SPAWN_Z)
	return CFrame.lookAt(position, position + V(0, 0, 1))
end

local function addLabel(part, text, color, offset)
	local gui = Instance.new("BillboardGui")
	gui.Size = UDim2.fromOffset(220, 50)
	gui.StudsOffset = offset or V(0, 3, 0)
	gui.MaxDistance = 90
	gui.LightInfluence = 0
	local label = Instance.new("TextLabel")
	label.BackgroundTransparency = 1
	label.Size = UDim2.fromScale(1, 1)
	label.Font = Enum.Font.GothamBlack
	label.TextScaled = true
	label.TextColor3 = color
	label.TextStrokeTransparency = 0
	label.RichText = true
	label.Text = text
	label.Parent = gui
	gui.Parent = part
end

local function buildTree(folder, base)
	makePart({ Name = "Log", Size = V(3, 12, 3), Position = base + V(0, 6, 0), Color = C(102, 81, 50), Parent = folder })
	local leaves = C(60, 130, 40)
	makePart({ Name = "Leaves", Size = V(15, 6, 15), Position = base + V(0, 12, 0), Color = shade(leaves, 0.15), Parent = folder })
	makePart({ Name = "Leaves", Size = V(9, 5, 9), Position = base + V(0, 17, 0), Color = shade(leaves, 0.15), Parent = folder })
end

local goToWorld -- объявлена ниже
local openEgg -- объявлена ниже

local function buildPortal(folder, position, target)
	local frameColor = C(20, 16, 32)
	local portal = Instance.new("Model")
	portal.Name = "Portal_" .. target.name
	makePart({ Size = V(1.5, 9, 1.5), Position = position + V(-3.25, 4.5, 0), Color = frameColor, Parent = portal })
	makePart({ Size = V(1.5, 9, 1.5), Position = position + V(3.25, 4.5, 0), Color = frameColor, Parent = portal })
	local top = makePart({ Size = V(8, 1.5, 1.5), Position = position + V(0, 8.25, 0), Color = frameColor, Parent = portal })
	makePart({ Size = V(8, 1.5, 1.5), Position = position + V(0, 0.75, 0), Color = frameColor, Parent = portal })
	local inner = makePart({
		Name = "Inner",
		Size = V(5, 6, 0.4),
		Position = position + V(0, 4.5, 0),
		Color = target.accent,
		Material = NEON,
		Transparency = 0.3,
		CanCollide = false,
		Parent = portal,
	})
	addLabel(top, target.name .. (target.price > 0 and ("\n" .. EM .. " " .. abbreviate(target.price)) or ""), target.accent, V(0, 3, 0))

	local prompt = Instance.new("ProximityPrompt")
	prompt.ActionText = "Войти"
	prompt.ObjectText = target.name
	prompt.HoldDuration = 0.3
	prompt.MaxActivationDistance = 12
	prompt.RequiresLineOfSight = false
	prompt.Parent = inner
	prompt.Triggered:Connect(function(player)
		goToWorld(player, target.id)
	end)
	portal.Parent = folder
end

------------------------------------------------------------------
-- ФИГУРЫ ИЗ КУБИКОВ (мобы-НПС). Строятся лицом к -Z, ноги на земле (y = 0).
------------------------------------------------------------------
local function buildFigure(name, build)
	local model = Instance.new("Model")
	model.Name = name
	local function box(size, position, color, material, spots)
		local part = makePart({ Size = size, Position = position, Color = color, Material = material or SMOOTH, Parent = model })
		if spots then
			addSpots(model, part, spots, 4)
		end
		return part
	end
	local function pair(size, position, color, material, spots)
		box(size, position, color, material, spots)
		box(size, V(-position.X, position.Y, position.Z), color, material, spots)
	end
	build(box, pair)
	model.WorldPivot = CFrame.new()
	return model
end

local function creeperNpc()
	return buildFigure("Крипер", function(box, pair)
		local green = C(85, 165, 75)
		local camo = { C(60, 140, 55), C(115, 200, 95), C(45, 110, 45), C(150, 220, 130) }
		pair(V(1, 1.5, 1), V(0.5, 0.75, -1), green, nil, camo)
		pair(V(1, 1.5, 1), V(0.5, 0.75, 1), green, nil, camo)
		box(V(2, 3, 1), V(0, 3, 0), green, nil, camo)
		box(V(2, 2, 2), V(0, 5.5, 0), green, nil, camo)
		local face = C(25, 35, 25)
		pair(V(0.5, 0.5, 0.2), V(0.5, 5.75, -1.06), face)
		box(V(0.5, 0.75, 0.2), V(0, 5.125, -1.06), face)
		pair(V(0.25, 0.75, 0.2), V(0.375, 4.875, -1.06), face)
	end)
end

local function endermanNpc()
	local model = buildFigure("Эндермен", function(box, pair)
		local black = C(18, 18, 22)
		local dark = { C(32, 32, 40), C(26, 26, 32) }
		pair(V(0.36, 5.4, 0.36), V(0.36, 2.7, 0), black, nil, dark)
		box(V(1.44, 2.16, 0.72), V(0, 6.48, 0), black, nil, dark)
		pair(V(0.36, 5.4, 0.36), V(0.9, 4.86, 0), black, nil, dark)
		box(V(1.44, 1.44, 1.44), V(0, 8.28, 0), black, nil, dark)
		pair(V(0.54, 0.18, 0.16), V(0.4, 8.19, -0.78), C(225, 120, 255), NEON)
		pair(V(0.18, 0.18, 0.2), V(0.31, 8.19, -0.8), C(255, 215, 255), NEON)
	end)
	local body = model:FindFirstChildWhichIsA("BasePart")
	if body then
		local particles = Instance.new("ParticleEmitter")
		particles.Color = ColorSequence.new(C(200, 90, 255))
		particles.LightEmission = 1
		particles.Size = NumberSequence.new(0.25)
		particles.Rate = 10
		particles.Lifetime = NumberRange.new(1.5, 2.5)
		particles.Speed = NumberRange.new(0.5, 1.5)
		particles.SpreadAngle = Vector2.new(180, 180)
		particles.Parent = body
	end
	return model
end

local function villagerNpc(robe, apron)
	return buildFigure("Житель", function(box, pair)
		local skin = C(190, 135, 100)
		local skinSpots = { C(175, 120, 90), C(200, 145, 110) }
		box(V(1.76, 4.4, 1.32), V(0, 2.2, 0), robe, nil, { robe:Lerp(DARK, 0.2), robe:Lerp(WHITE, 0.1) })
		if apron then
			box(V(1.5, 3.0, 0.1), V(0, 2.0, -0.71), apron)
		end
		box(V(2.2, 0.7, 0.7), V(0, 3.3, -0.95), robe)
		box(V(0.9, 0.55, 0.12), V(0, 3.3, -1.32), skin)
		box(V(1.76, 2.2, 1.76), V(0, 5.5, 0), skin, nil, skinSpots)
		box(V(0.44, 0.88, 0.44), V(0, 5.0, -1.1), C(170, 115, 85))
		box(V(1.2, 0.2, 0.12), V(0, 5.85, -0.92), C(80, 55, 40))
		pair(V(0.4, 0.22, 0.12), V(0.36, 5.6, -0.92), C(240, 240, 240))
		pair(V(0.2, 0.22, 0.14), V(0.26, 5.6, -0.93), C(40, 140, 60))
	end)
end

------------------------------------------------------------------
-- ЛАВКА С НПС. Строится лицом к -Z, потом ставится на место.
------------------------------------------------------------------
local function buildStall(folder, cf, title, color, icon, windowName, npc)
	local model = Instance.new("Model")
	model.Name = "Stall_" .. windowName
	local wood, woodDark = C(200, 132, 62), C(150, 95, 45)
	local counter = makePart({ Name = "Counter", Size = V(12, 3.2, 2.6), Position = V(0, 1.6, -2), Color = wood, Parent = model })
	addSpots(model, counter, { woodDark, wood:Lerp(WHITE, 0.15) }, 5)
	makePart({ Size = V(12.6, 0.5, 3), Position = V(0, 3.45, -2), Color = woodDark, Parent = model })
	for _, x in { -5.7, 5.7 } do
		for _, z in { -3.4, 3.2 } do
			makePart({ Size = V(0.8, 10, 0.8), Position = V(x, 5, z), Color = woodDark, Parent = model })
		end
	end
	-- полосатый навес
	for i = 0, 5 do
		local stripe = i % 2 == 0 and color or WHITE
		makePart({ Size = V(2.2, 0.6, 7.8), Position = V(-5.5 + i * 2.2, 10.3, -0.1), Color = stripe, Parent = model })
		makePart({ Size = V(2.2, 0.9, 0.3), Position = V(-5.5 + i * 2.2, 9.6, -4.1), Color = stripe, Parent = model })
	end
	-- вывеска с пиксельной иконкой (иконку дорисует клиент)
	local sign = makePart({ Name = "Sign", Size = V(0.4, 0.4, 0.4), Position = V(0, 12, -2), Transparency = 1, CanCollide = false, Parent = model })
	local gui = Instance.new("BillboardGui")
	gui.Size = UDim2.fromOffset(280, 70)
	gui.MaxDistance = 140
	gui.LightInfluence = 0
	gui.Parent = sign
	local slot = Instance.new("Frame")
	slot.Name = "PixelIconSlot"
	slot.BackgroundTransparency = 1
	slot.Size = UDim2.fromOffset(64, 64)
	slot.Position = UDim2.fromOffset(0, 3)
	slot:SetAttribute("Icon", icon)
	slot.Parent = gui
	local text = Instance.new("TextLabel")
	text.BackgroundTransparency = 1
	text.Position = UDim2.fromOffset(70, 0)
	text.Size = UDim2.new(1, -70, 1, 0)
	text.Font = Enum.Font.GothamBlack
	text.TextScaled = true
	text.TextXAlignment = Enum.TextXAlignment.Left
	text.TextColor3 = WHITE
	text.Text = title
	text.Parent = gui
	local stroke = Instance.new("UIStroke")
	stroke.Thickness = 3
	stroke.Color = C(15, 15, 20)
	stroke.Parent = text

	npc:PivotTo(CFrame.new(0, 0, 1))
	npc.Parent = model

	local prompt = Instance.new("ProximityPrompt")
	prompt.ActionText = "Открыть"
	prompt.ObjectText = title
	prompt.HoldDuration = 0
	prompt.MaxActivationDistance = 12
	prompt.RequiresLineOfSight = false
	prompt.Parent = counter
	prompt.Triggered:Connect(function(player)
		uiRemote:FireClient(player, "open", windowName)
	end)

	model.WorldPivot = CFrame.new()
	model:PivotTo(cf)
	model.Parent = folder
end

------------------------------------------------------------------
-- ЯЙЦО ПРИЗЫВА (как в Майнкрафте). Строится лицом к -Z.
------------------------------------------------------------------
local function colorHex(color)
	return string.format("#%02X%02X%02X", math.floor(color.R * 255), math.floor(color.G * 255), math.floor(color.B * 255))
end

local function buildSpawnEgg(folder, cf, world, creeperFace)
	local egg = world.egg
	local model = Instance.new("Model")
	model.Name = "SpawnEgg_" .. world.id
	local pedestal = makePart({ Name = "Pedestal", Size = V(8, 2, 8), Position = V(0, 1, 0), Color = C(75, 75, 85), Parent = model })
	addSpots(model, pedestal, { C(60, 60, 70), C(100, 100, 110) }, 5)

	local voxel, layers, radius, middle = 0.8, 11, 3.2, 0.4
	local spotColors = { egg.spot, egg.spot:Lerp(DARK, 0.2), egg.color:Lerp(WHITE, 0.25) }
	local layerDepth = {}
	for i = 0, layers - 1 do
		local t = (i + 0.5) / layers
		local k = t < middle and (t - middle) / middle or (t - middle) / (1 - middle)
		local r = radius * math.sqrt(math.max(0.06, 1 - k * k))
		local wide = math.max(voxel, math.floor(r * 2 / voxel + 0.5) * voxel)
		local narrow = math.max(voxel, math.floor(r * 1.25 / voxel + 0.5) * voxel)
		local y = 2 + voxel * (i + 0.5)
		makePart({ Size = V(wide, voxel, narrow), Position = V(0, y, 0), Color = egg.color, Parent = model })
		makePart({ Size = V(narrow, voxel, wide), Position = V(0, y, 0), Color = egg.color, Parent = model })
		layerDepth[i] = wide
		-- пятна
		for _ = 1, 3 do
			local along = (math.random(0, math.max(0, narrow / voxel - 1)) - (narrow / voxel - 1) / 2) * voxel
			local out = wide / 2 + 0.03
			local side = math.random(1, 4)
			local offset = side == 1 and V(along, 0, -out) or side == 2 and V(along, 0, out) or side == 3 and V(-out, 0, along) or V(out, 0, along)
			local thin = (side <= 2) and V(voxel, voxel, 0.1) or V(0.1, voxel, voxel)
			makePart({ Size = thin, Position = V(0, y, 0) + offset, Color = spotColors[math.random(1, #spotColors)], CanCollide = false, Parent = model })
		end
	end
	if creeperFace then
		local face = C(20, 40, 20)
		local function facePixel(x, layer, height)
			local z = -layerDepth[layer] / 2 - 0.08
			makePart({ Size = V(voxel, voxel * height, 0.16), Position = V(x, 2 + voxel * (layer + height / 2), z), Color = face, CanCollide = false, Parent = model })
		end
		facePixel(-voxel, 7, 1)
		facePixel(voxel, 7, 1)
		facePixel(0, 4, 2)
		facePixel(-voxel, 3, 2)
		facePixel(voxel, 3, 2)
	end

	-- надпись: название, цена и шансы питомцев
	local lines = { '<font size="34">' .. egg.name .. "</font>", EM .. " " .. abbreviate(egg.price) }
	local total = 0
	for _, entry in egg.pets do
		total += entry.weight
	end
	for _, entry in egg.pets do
		local rarity = RARITIES[PETS[entry.kind].rarity]
		local chance = entry.weight / total * 100
		local chanceText = chance < 1 and string.format("%.1f%%", chance) or (math.floor(chance + 0.5) .. "%")
		table.insert(lines, '<font color="' .. colorHex(rarity.color) .. '">' .. entry.kind .. "</font>  " .. chanceText)
	end
	local top = makePart({ Name = "Top", Size = V(0.4, 0.4, 0.4), Position = V(0, 12, 0), Transparency = 1, CanCollide = false, Parent = model })
	local gui = Instance.new("BillboardGui")
	gui.Size = UDim2.fromOffset(240, 34 * #lines)
	gui.StudsOffset = V(0, 1.5 + #lines * 0.45, 0)
	gui.MaxDistance = 70
	gui.LightInfluence = 0
	gui.Parent = top
	local text = Instance.new("TextLabel")
	text.BackgroundTransparency = 1
	text.Size = UDim2.fromScale(1, 1)
	text.Font = Enum.Font.GothamBlack
	text.TextScaled = true
	text.RichText = true
	text.TextColor3 = WHITE
	text.Text = table.concat(lines, "\n")
	text.Parent = gui
	local stroke = Instance.new("UIStroke")
	stroke.Thickness = 2.5
	stroke.Color = C(15, 15, 20)
	stroke.Parent = text

	local prompt = Instance.new("ProximityPrompt")
	prompt.ActionText = "Открыть яйцо"
	prompt.ObjectText = egg.name .. " (" .. abbreviate(egg.price) .. " изумрудов)"
	prompt.HoldDuration = 0
	prompt.MaxActivationDistance = 12
	prompt.RequiresLineOfSight = false
	prompt.Parent = pedestal
	prompt.Triggered:Connect(function(player)
		openEgg(player, world.id)
	end)

	model.WorldPivot = CFrame.new()
	model:PivotTo(cf)
	model.Parent = folder
end

------------------------------------------------------------------
-- ГЛАВНЫЙ МИР «Луга»: хаб с НПС, забор-«линия» и зона добычи:
-- остров посреди озера, холмы-террасы с деревьями, ручьи и водопады
------------------------------------------------------------------
local ISLAND_CENTER = V(0, 0, 45)
local ISLAND_RADIUS = 48
local LAKE_RADIUS = 64
local GATE_Z = -60
local HUB = { minX = -60, maxX = 60, minZ = -168, maxZ = GATE_Z }
local MAIN_BOUNDS = { minX = -126, maxX = 126, minZ = -174, maxZ = 156 }
local STREAM_ANGLES = { 30, 90, 150, 205 }
local WATER = C(55, 125, 220)
local FALL = C(110, 180, 250)

local function mainTileKind(x, z)
	if x >= HUB.minX and x <= HUB.maxX and z >= HUB.minZ and z <= HUB.maxZ then
		return "hub", 0
	end
	local dx, dz = x - ISLAND_CENTER.X, z - ISLAND_CENTER.Z
	local r = math.sqrt(dx * dx + dz * dz)
	if math.abs(x) <= 9 and z < ISLAND_CENTER.Z and r >= LAKE_RADIUS then
		return "path", r
	end
	if r < ISLAND_RADIUS - 3 then
		return "island", r
	elseif r < ISLAND_RADIUS then
		return "sand", r
	elseif r < LAKE_RADIUS then
		return "water", r
	end
	return "hill", r
end

local function buildCherry(folder, base)
	makePart({ Name = "Log", Size = V(2.4, 10, 2.4), Position = base + V(0, 5, 0), Color = C(75, 45, 45), Parent = folder })
	local pink = C(245, 175, 205)
	makePart({ Name = "Leaves", Size = V(14, 5, 14), Position = base + V(0, 11, 0), Color = shade(pink, 0.12), Parent = folder })
	makePart({ Name = "Leaves", Size = V(9, 4, 9), Position = base + V(0, 15, 0), Color = shade(pink, 0.12), Parent = folder })
end

local function buildMainWorld(world)
	local T = CONFIG.TileSize
	local folder = Instance.new("Folder")
	folder.Name = world.name
	local spots = {}
	spawnSpots[world.id] = spots

	-- 1. Высоты холмов
	local tiles = {}
	for x = MAIN_BOUNDS.minX + T / 2, MAIN_BOUNDS.maxX, T do
		for z = MAIN_BOUNDS.minZ + T / 2, MAIN_BOUNDS.maxZ, T do
			local kind, r = mainTileKind(x, z)
			local height = 0
			if kind == "hill" then
				local bump = math.floor((math.noise(x / 40, z / 40, 3.7) + 0.5) * 2.5)
				height = math.clamp(1 + math.floor((r - LAKE_RADIUS) / 7) + bump, 1, 10)
			end
			tiles[x .. "," .. z] = { x = x, z = z, kind = kind, r = r, height = height }
		end
	end
	local function topOf(tile)
		if not tile then
			return -0.6
		end
		if tile.kind == "hill" then
			return tile.height * 4
		elseif tile.kind == "water" then
			return -0.6
		end
		return 0
	end
	local function tileAt(x, z)
		local tx = math.floor((x - MAIN_BOUNDS.minX) / T) * T + MAIN_BOUNDS.minX + T / 2
		local tz = math.floor((z - MAIN_BOUNDS.minZ) / T) * T + MAIN_BOUNDS.minZ + T / 2
		return tiles[tx .. "," .. tz]
	end
	local function streamAngle(tile)
		local dx, dz = tile.x - ISLAND_CENTER.X, tile.z - ISLAND_CENTER.Z
		for _, angle in STREAM_ANGLES do
			local a = math.rad(angle)
			local along = dx * math.cos(a) + dz * math.sin(a)
			local perp = math.abs(-dx * math.sin(a) + dz * math.cos(a))
			if along > 0 and perp < 3.6 and tile.r < 112 then
				return a
			end
		end
		return nil
	end

	-- 2. Строим плитки
	for _, tile in tiles do
		local x, z = tile.x, tile.z
		if tile.kind == "hub" then
			local path = math.abs(x) <= 6
			makePart({
				Name = path and "Path" or "Grass",
				Size = V(T, 2, T),
				Position = V(x, -1, z),
				Color = shade(path and C(140, 135, 128) or C(84, 140, 50), 0.2),
				Parent = folder,
			})
		elseif tile.kind == "path" then
			makePart({
				Name = "Path",
				Size = V(T, 2, T),
				Position = V(x, -1, z),
				Color = shade(math.abs(x) <= 6 and C(140, 135, 128) or C(84, 140, 50), 0.2),
				Parent = folder,
			})
		elseif tile.kind == "island" then
			makePart({ Name = "Grass", Size = V(T, 2, T), Position = V(x, -1, z), Color = shade(C(88, 148, 52), 0.2), Parent = folder })
			if tile.r < ISLAND_RADIUS - 6 then
				table.insert(spots, { key = x .. "," .. z, x = x, z = z })
			end
		elseif tile.kind == "sand" then
			makePart({ Name = "Sand", Size = V(T, 2, T), Position = V(x, -1, z), Color = shade(C(220, 205, 150), 0.12), Parent = folder })
		elseif tile.kind == "water" then
			makePart({ Name = "LakeBed", Size = V(T, 2, T), Position = V(x, -4, z), Color = shade(C(200, 185, 135), 0.15), Parent = folder })
			makePart({
				Name = "Water",
				Size = V(T, 0.4, T),
				Position = V(x, -0.8, z),
				Color = shade(WATER, 0.08),
				Transparency = 0.3,
				CanCollide = false,
				CanQuery = false,
				Parent = folder,
			})
		else
			local top = tile.height * 4
			makePart({ Name = "Hill", Size = V(T, top + 2, T), Position = V(x, top / 2 - 1, z), Color = shade(C(134, 96, 67), 0.15), Parent = folder })
			local angle = streamAngle(tile)
			if angle then
				-- ручей сверху и водопад вниз, к следующей ступеньке
				makePart({
					Name = "Stream",
					Size = V(T + 0.02, 0.4, T + 0.02),
					Position = V(x, top + 0.1, z),
					Color = shade(WATER, 0.08),
					Transparency = 0.15,
					CanCollide = false,
					Parent = folder,
				})
				local inward = V(-math.cos(angle), 0, -math.sin(angle))
				local below = topOf(tileAt(x + inward.X * T, z + inward.Z * T))
				local drop = top - below
				if drop > 0.5 then
					local edge = V(x, below + drop / 2 + 0.1, z) + inward * (T / 2)
					local fall = makePart({
						Name = "Waterfall",
						Size = V(T - 0.6, drop, 0.6),
						Color = FALL,
						Transparency = 0.1,
						CanCollide = false,
						Parent = folder,
					})
					fall.CFrame = CFrame.lookAt(edge, edge + inward)
					makePart({
						Name = "Foam",
						Size = V(T, 0.3, 2.5),
						Color = WHITE,
						Transparency = 0.25,
						CanCollide = false,
						CFrame = CFrame.lookAt(V(edge.X, below + 0.2, edge.Z), V(edge.X, below + 0.2, edge.Z) + inward) * CFrame.new(0, 0, -1),
						Parent = folder,
					})
				end
			else
				makePart({ Name = "Grass", Size = V(T + 0.05, 1.2, T + 0.05), Position = V(x, top - 0.58, z), Color = shade(C(95, 155, 55), 0.15), Parent = folder })
				if tile.height <= 7 and math.random() < 0.13 then
					if math.random() < 0.55 then
						buildCherry(folder, V(x, top, z))
					else
						buildTree(folder, V(x, top, z))
					end
				end
			end
		end
	end

	-- 3. Мост через озеро
	local bridgeFrom = ISLAND_CENTER.Z - LAKE_RADIUS - 2
	local bridgeTo = ISLAND_CENTER.Z - ISLAND_RADIUS + 3
	for z = bridgeFrom, bridgeTo, 2 do
		makePart({ Name = "Plank", Size = V(10, 1, 2), Position = V(0, -0.5, z + 1), Color = shade(C(170, 120, 65), 0.15), Parent = folder })
	end
	for _, x in { -5, 5 } do
		makePart({
			Name = "Rail",
			Size = V(0.6, 0.5, bridgeTo - bridgeFrom + 2),
			Position = V(x, 2, (bridgeFrom + bridgeTo) / 2 + 1),
			Color = C(120, 80, 40),
			Parent = folder,
		})
		for z = bridgeFrom, bridgeTo + 2, 4 do
			makePart({ Name = "Post", Size = V(0.7, 2.4, 0.7), Position = V(x, 1, z), Color = C(110, 72, 36), Parent = folder })
		end
	end

	-- 4. Забор-«линия» с воротами в зону добычи
	for x = HUB.minX, HUB.maxX, 3 do
		if math.abs(x) > 9 then
			makePart({ Name = "Fence", Size = V(0.7, 3, 0.7), Position = V(x, 1.5, GATE_Z), Color = C(130, 88, 45), Parent = folder })
		end
	end
	for _, side in { -1, 1 } do
		local from, to = 9.5 * side, HUB.maxX * side
		makePart({
			Name = "FenceRail",
			Size = V(math.abs(to - from), 0.4, 0.4),
			Position = V((from + to) / 2, 2.4, GATE_Z),
			Color = C(150, 100, 52),
			Parent = folder,
		})
		makePart({
			Name = "FenceRail",
			Size = V(math.abs(to - from), 0.4, 0.4),
			Position = V((from + to) / 2, 1.2, GATE_Z),
			Color = C(150, 100, 52),
			Parent = folder,
		})
		makePart({ Name = "GatePost", Size = V(1.4, 11, 1.4), Position = V(10 * side, 5.5, GATE_Z), Color = C(110, 72, 36), Parent = folder })
	end
	local gateBeam = makePart({ Name = "GateBeam", Size = V(22, 1.6, 1.6), Position = V(0, 11.5, GATE_Z), Color = C(110, 72, 36), Parent = folder })
	addLabel(gateBeam, "ЗОНА ДОБЫЧИ", C(110, 255, 80), V(0, 2.5, 0))
	for i = -9, 8 do
		makePart({
			Name = "Line",
			Size = V(1, 0.1, 1),
			Position = V(i + 0.5, 0.05, GATE_Z + 0.5),
			Color = i % 2 == 0 and WHITE or C(25, 25, 25),
			CanCollide = false,
			Parent = folder,
		})
		makePart({
			Name = "Line",
			Size = V(1, 0.1, 1),
			Position = V(i + 0.5, 0.05, GATE_Z - 0.5),
			Color = i % 2 == 0 and C(25, 25, 25) or WHITE,
			CanCollide = false,
			Parent = folder,
		})
	end

	-- 5. Хаб: спавн, лавки с мобами, яйцо, порталы
	local pad = Instance.new("SpawnLocation")
	pad.Anchored = true
	pad.Duration = 0
	pad.Neutral = true
	pad.Size = V(14, 1, 14)
	pad.Position = HUB_SPAWN
	pad.Material = NEON
	pad.Color = world.accent
	pad.TopSurface = Enum.SurfaceType.Smooth
	pad.Parent = folder

	local function facing(position, target)
		return CFrame.lookAt(position, V(target.X, position.Y, target.Z))
	end
	buildStall(folder, facing(V(-24, 0, -112), V(0, 0, -112)), "Улучшения", C(80, 190, 70), "uparrow", "upgrades", creeperNpc())
	buildStall(folder, facing(V(-24, 0, -86), V(0, 0, -86)), "Ауры", C(150, 70, 230), "fire", "auras", endermanNpc())
	buildStall(folder, facing(V(24, 0, -112), V(0, 0, -112)), "Кирки", C(235, 130, 35), "pickaxe", "pickaxes", villagerNpc(C(115, 80, 50), C(55, 50, 50)))
	buildStall(folder, facing(V(24, 0, -86), V(0, 0, -86)), "Магазин", C(50, 130, 230), "potion", "shop", villagerNpc(C(45, 75, 150), nil))

	buildSpawnEgg(folder, facing(V(-30, 0, -134), V(0, 0, -134)), world, true)

	local portalX = 28
	for _, target in WORLDS do
		if target.id ~= world.id then
			buildPortal(folder, V(portalX, 0, -128), target)
			portalX += 12
		end
	end

	for _ = 1, 40 do
		local x, z = math.random(-56, 56), math.random(-164, -66)
		if math.abs(x) > 10 and math.abs(math.abs(x) - 24) > 8 then
			makePart({ Name = "Stem", Size = V(0.3, 1.2, 0.3), Position = V(x, 0.6, z), Color = C(60, 140, 40), CanCollide = false, Parent = folder })
			makePart({
				Name = "Flower",
				Size = V(0.8, 0.5, 0.8),
				Position = V(x, 1.4, z),
				Color = math.random() < 0.5 and C(230, 50, 50) or C(250, 220, 50),
				CanCollide = false,
				Parent = folder,
			})
		end
	end

	-- 6. Невидимые стены по краю
	local cx, cz = (MAIN_BOUNDS.minX + MAIN_BOUNDS.maxX) / 2, (MAIN_BOUNDS.minZ + MAIN_BOUNDS.maxZ) / 2
	local w, d = MAIN_BOUNDS.maxX - MAIN_BOUNDS.minX, MAIN_BOUNDS.maxZ - MAIN_BOUNDS.minZ
	for _, wall in {
		{ V(cx, 60, MAIN_BOUNDS.minZ), V(w, 140, 2) },
		{ V(cx, 60, MAIN_BOUNDS.maxZ), V(w, 140, 2) },
		{ V(MAIN_BOUNDS.minX, 60, cz), V(2, 140, d) },
		{ V(MAIN_BOUNDS.maxX, 60, cz), V(2, 140, d) },
	} do
		makePart({ Name = "Barrier", Position = wall[1], Size = wall[2], Transparency = 1, Parent = folder })
	end
	for _ = 1, 12 do
		makePart({
			Name = "Cloud",
			Size = V(math.random(20, 40), 4, math.random(12, 24)),
			Position = V(math.random(-180, 180), math.random(90, 110), math.random(-200, 180)),
			Color = WHITE,
			Transparency = 0.1,
			CanCollide = false,
			Parent = folder,
		})
	end

	folder.Parent = worldsFolder
end

local function buildWorld(world)
	if world.id == 1 then
		buildMainWorld(world)
		return
	end
	local T = CONFIG.TileSize
	local half = CONFIG.WorldTiles / 2
	local ringSize = 3
	local origin = worldOrigin(world.id)
	local theme = THEMES[world.theme]
	local folder = Instance.new("Folder")
	folder.Name = world.name

	-- Пол из плиток
	for i = -half, half - 1 do
		for j = -half, half - 1 do
			local color = theme.floor
			local material = SMOOTH
			if theme.floorAlt and math.random() < 0.15 then
				color = theme.floorAlt
			end
			if world.theme == "nether" and math.random() < 0.03 then
				color = C(255, 120, 30)
				material = NEON
			end
			makePart({
				Name = "Floor",
				Size = V(T, 2, T),
				Position = origin + V((i + 0.5) * T, -1, (j + 0.5) * T),
				Color = shade(color, 0.2),
				Material = material,
				Parent = folder,
			})
		end
	end

	-- Холмы по краю арены
	for i = -half - ringSize, half + ringSize - 1 do
		for j = -half - ringSize, half + ringSize - 1 do
			local inside = i >= -half and i < half and j >= -half and j < half
			if inside then
				continue
			end
			local depth = math.max(-half - i, i - (half - 1), -half - j, j - (half - 1)) -- 1..3
			local height = math.clamp(1 + math.floor((math.noise(i * 0.22, j * 0.22, world.id * 7.3) + 0.5) * 3), 1, 3) + depth - 1
			local x, z = origin.X + (i + 0.5) * T, (j + 0.5) * T
			local topY = -2 + height * T
			makePart({
				Name = "Hill",
				Size = V(T, height * T, T),
				Position = V(x, -2 + height * T / 2, z),
				Color = shade(theme.column, 0.12),
				Parent = folder,
			})
			if world.theme == "overworld" then
				makePart({
					Name = "Grass",
					Size = V(T + 0.05, 1.2, T + 0.05),
					Position = V(x, topY - 0.58, z),
					Color = shade(theme.columnTop, 0.12),
					Parent = folder,
				})
				if math.random() < 0.18 then
					buildTree(folder, V(x, topY, z))
				end
			elseif world.theme == "nether" then
				if math.random() < 0.15 then
					makePart({
						Name = "Lava",
						Size = V(T, 0.4, T),
						Position = V(x, topY + 0.2, z),
						Color = C(255, 120, 20),
						Material = NEON,
						Parent = folder,
					})
				end
				if math.random() < 0.06 then
					makePart({
						Name = "Glowstone",
						Size = V(4, 4, 4),
						Position = V(x, topY + 18 + math.random() * 20, z),
						Color = C(255, 205, 120),
						Material = NEON,
						Parent = folder,
					})
				end
			elseif world.theme == "ender" and depth == ringSize and math.random() < 0.15 then
				local pillarHeight = 30 + math.random() * 30
				makePart({
					Name = "Obsidian",
					Size = V(T, pillarHeight, T),
					Position = V(x, topY + pillarHeight / 2, z),
					Color = C(20, 16, 32),
					Parent = folder,
				})
				local crystal = makePart({
					Name = "Crystal",
					Size = V(3, 3, 3),
					Position = V(x, topY + pillarHeight + 2.5, z),
					Color = C(255, 140, 255),
					Material = NEON,
					Parent = folder,
				})
				local light = Instance.new("PointLight")
				light.Color = C(255, 140, 255)
				light.Range = 20
				light.Parent = crystal
			end
		end
	end

	-- Невидимые стены, чтобы не упасть с края
	local edge = (half + ringSize) * T
	for _, wall in {
		{ V(0, 50, edge), V(edge * 2, 120, 2) },
		{ V(0, 50, -edge), V(edge * 2, 120, 2) },
		{ V(edge, 50, 0), V(2, 120, edge * 2) },
		{ V(-edge, 50, 0), V(2, 120, edge * 2) },
	} do
		makePart({ Name = "Barrier", Position = origin + wall[1], Size = wall[2], Transparency = 1, Parent = folder })
	end

	-- Украшения
	if world.theme == "overworld" then
		for _ = 1, 30 do
			local i, j = math.random(-half, half - 1), math.random(-half + 5, half - 1)
			local base = origin + V((i + 0.5) * T + math.random(-2, 2), 0, (j + 0.5) * T + math.random(-2, 2))
			makePart({ Name = "Stem", Size = V(0.3, 1.2, 0.3), Position = base + V(0, 0.6, 0), Color = C(60, 140, 40), CanCollide = false, Parent = folder })
			makePart({
				Name = "Flower",
				Size = V(0.8, 0.5, 0.8),
				Position = base + V(0, 1.4, 0),
				Color = math.random() < 0.5 and C(230, 50, 50) or C(250, 220, 50),
				CanCollide = false,
				Parent = folder,
			})
		end
		for _ = 1, 10 do
			makePart({
				Name = "Cloud",
				Size = V(math.random(20, 40), 4, math.random(12, 24)),
				Position = origin + V(math.random(-180, 180), math.random(80, 100), math.random(-180, 180)),
				Color = WHITE,
				Transparency = 0.1,
				CanCollide = false,
				Parent = folder,
			})
		end
	elseif world.theme == "ender" then
		for _ = 1, 6 do
			local angle = math.random() * math.pi * 2
			local distance = 150 + math.random() * 60
			local center = origin + V(math.cos(angle) * distance, math.random(-10, 30), math.sin(angle) * distance)
			makePart({ Name = "Island", Size = V(18, 6, 18), Position = center, Color = shade(theme.floor, 0.1), Parent = folder })
			makePart({ Name = "Island", Size = V(10, 6, 10), Position = center - V(0, 6, 0), Color = shade(theme.floor, 0.1), Parent = folder })
		end
	end

	-- Точка появления
	local padPosition = origin + V(0, 0.5, SPAWN_Z)
	local pad
	if world.id == 1 then
		pad = Instance.new("SpawnLocation")
		pad.Anchored = true
		pad.Duration = 0
		pad.Neutral = true
		pad.Size = V(18, 1, 18)
		pad.Position = padPosition
		pad.Material = NEON
		pad.Color = world.accent
		pad.TopSurface = Enum.SurfaceType.Smooth
		pad.Parent = folder
	else
		pad = makePart({ Name = "Spawn", Size = V(18, 1, 18), Position = padPosition, Color = world.accent, Material = NEON, Parent = folder })
	end

	-- Порталы в другие миры
	local portalX = -24
	for _, target in WORLDS do
		if target.id ~= world.id then
			buildPortal(folder, origin + V(portalX, 0, SPAWN_Z), target)
			portalX -= 12
		end
	end

	-- Яйцо этого мира
	buildSpawnEgg(folder, CFrame.lookAt(origin + V(26, 0, SPAWN_Z), origin + V(0, 0, SPAWN_Z)), world, false)

	-- Где появляются блоки
	local spots = {}
	for i = -half + 1, half - 2 do
		for j = -half + 5, half - 2 do
			table.insert(spots, { key = i .. "," .. j, x = origin.X + (i + 0.5) * T, z = (j + 0.5) * T })
		end
	end
	spawnSpots[world.id] = spots

	folder.Parent = worldsFolder
end

------------------------------------------------------------------
-- ЭФФЕКТЫ
------------------------------------------------------------------
local function popup(position, text, color, big)
	local anchor = makePart({
		Name = "Popup",
		Size = V(0.2, 0.2, 0.2),
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
	label.RichText = true
	label.Text = text
	label.Parent = gui
	local outline = Instance.new("UIStroke")
	outline.Thickness = big and 3 or 2
	outline.Color = C(15, 15, 25)
	outline.Parent = label

	TweenService:Create(outline, TweenInfo.new(0.5, Enum.EasingStyle.Linear, Enum.EasingDirection.In, 0, false, 0.4), {
		Transparency = 1,
	}):Play()
	TweenService:Create(anchor, TweenInfo.new(0.9, Enum.EasingStyle.Quad, Enum.EasingDirection.Out), {
		Position = position + V(0, 4, 0),
	}):Play()
	TweenService:Create(label, TweenInfo.new(0.5, Enum.EasingStyle.Linear, Enum.EasingDirection.In, 0, false, 0.4), {
		TextTransparency = 1,
	}):Play()
	Debris:AddItem(anchor, 1)
end

local function shatter(core, def)
	local colors = { def.color, def.color:Lerp(DARK, 0.15) }
	if def.spot then
		table.insert(colors, def.spot)
	end
	if def.top then
		table.insert(colors, def.top)
	end
	local pieces = def.rare and 16 or 10
	local s = core.Size.X
	for _ = 1, pieces do
		local piece = makePart({
			Name = "Piece",
			Anchored = false,
			Size = V(s, s, s) / (4 + math.random() * 2),
			CFrame = core.CFrame + V((math.random() - 0.5) * s, (math.random() - 0.5) * s, (math.random() - 0.5) * s),
			Color = colors[math.random(1, #colors)],
			Material = core.Material,
			CanQuery = false,
			CanTouch = false,
			Parent = effectsFolder,
		})
		piece.AssemblyLinearVelocity = V((math.random() - 0.5) * 50, 25 + math.random() * 30, (math.random() - 0.5) * 50)
		piece.AssemblyAngularVelocity = V(math.random() * 10, math.random() * 10, math.random() * 10)
		Debris:AddItem(piece, 2.5)
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

local profiles = {} -- [player] = { worlds, pets, equipped, pickaxe, addons, loaded }

local function stat(player, name)
	local stats = player:FindFirstChild("leaderstats")
	return stats and stats:FindFirstChild(name)
end

local function findPet(profile, id)
	for index, pet in profile.pets do
		if pet.id == id then
			return pet, index
		end
	end
	return nil, nil
end

local function addonLevel(player, id)
	local profile = profiles[player]
	return profile and profile.addons[id] or 0
end

local function petBonus(player)
	local profile = profiles[player]
	local total = 0
	if profile then
		for _, id in profile.equipped do
			local pet = findPet(profile, id)
			if pet and PETS[pet.kind] then
				total += PETS[pet.kind].bonus
			end
		end
	end
	return total
end

local function petSlots(player)
	local profile = profiles[player]
	return profile and profile.petSlots or BASE_PET_SLOTS
end

local function boostActive(player, id)
	local profile = profiles[player]
	return profile ~= nil and (profile.boosts[id] or 0) > os.time()
end

local function auraBoost(player)
	local profile = profiles[player]
	local aura = profile and profile.aura and AURA_BY_ID[profile.aura]
	return aura and aura.boost or 1
end

local function coinMultiplier(player)
	local rebirths = stat(player, REBIRTH_STAT)
	local rebirthMult = 1 + (rebirths and rebirths.Value or 0) * CONFIG.RebirthBonus
	local wealth = boostActive(player, "wealth") and 2 or 1
	return rebirthMult * (1 + petBonus(player)) * (1 + addonLevel(player, "fortune") * 0.25) * auraBoost(player) * wealth
end

local function damageOf(player)
	local level = player:GetAttribute("Level") or 1
	local profile = profiles[player]
	local pickaxe = PICKAXES[profile and profile.pickaxe or 1]
	local efficiency = 1 + addonLevel(player, "efficiency") * 0.2
	local power = boostActive(player, "power") and 2 or 1
	return math.max(1, math.floor(level ^ 1.5 * pickaxe.damage * efficiency * power))
end

-- Обновляет атрибуты, которые читает интерфейс игрока
local function refreshPlayer(player)
	local profile = profiles[player]
	local rebirths = stat(player, REBIRTH_STAT)
	if not profile or not rebirths then
		return
	end
	local level = player:GetAttribute("Level") or 1
	player:SetAttribute("Damage", damageOf(player))
	player:SetAttribute("LevelCost", levelCost(level))
	player:SetAttribute("RebirthCost", rebirthCost(rebirths.Value))
	player:SetAttribute("PetBonus", petBonus(player))
	player:SetAttribute("CoinMultiplier", math.floor(coinMultiplier(player) * 100 + 0.5) / 100)
	player:SetAttribute("Pickaxe", profile.pickaxe)
	player:SetAttribute("PetSlots", profile.petSlots)
	player:SetAttribute("Aura", profile.aura or "")
	local owned = {}
	for id in profile.auras do
		table.insert(owned, id)
	end
	player:SetAttribute("Auras", table.concat(owned, ","))
	for _, potion in POTIONS do
		player:SetAttribute("Boost_" .. potion.id, profile.boosts[potion.id] or 0)
	end
	for _, addon in ADDONS do
		player:SetAttribute("Addon_" .. addon.id, profile.addons[addon.id] or 0)
	end
	local worlds = {}
	for id in profile.worlds do
		table.insert(worlds, id)
	end
	table.sort(worlds)
	player:SetAttribute("Worlds", table.concat(worlds, ","))
	local kinds = {}
	for _, id in profile.equipped do
		local pet = findPet(profile, id)
		if pet then
			table.insert(kinds, pet.kind)
		end
	end
	player:SetAttribute("EquippedPets", table.concat(kinds, ","))
end

local function sendInventory(player)
	local profile = profiles[player]
	if profile then
		inventoryRemote:FireClient(player, { pets = profile.pets, equipped = profile.equipped })
	end
end

local function refreshPets(player)
	refreshPlayer(player)
	sendInventory(player)
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

	local profile = {
		loaded = loaded,
		worlds = { [1] = true },
		pets = {},
		equipped = {},
		pickaxe = math.clamp(saved.pickaxe or 1, 1, #PICKAXES),
		addons = {},
		playtime = saved.playtime or 0, -- секунд в игре
		earned = saved.earned or saved.coins or 0, -- монет заработано за всё время
		robux = saved.robux or 0, -- потрачено Robux
		petSlots = math.clamp(saved.petSlots or BASE_PET_SLOTS, BASE_PET_SLOTS, MAX_PET_SLOTS),
		auras = {},
		aura = nil,
		boosts = {},
	}
	for _, id in saved.auras or {} do
		if AURA_BY_ID[id] then
			profile.auras[id] = true
		end
	end
	if saved.aura and profile.auras[saved.aura] then
		profile.aura = saved.aura
	end
	for _, potion in POTIONS do
		local ends = saved.boosts and saved.boosts[potion.id]
		if typeof(ends) == "number" then
			profile.boosts[potion.id] = ends
		end
	end
	for _, id in saved.worlds or {} do
		if WORLDS[id] then
			profile.worlds[id] = true
		end
	end
	for _, pet in saved.pets or {} do
		if PETS[pet.kind] and #profile.pets < CONFIG.MaxPets then
			table.insert(profile.pets, { id = pet.id, kind = pet.kind })
		end
	end
	for _, id in saved.equipped or {} do
		if findPet(profile, id) and #profile.equipped < profile.petSlots then
			table.insert(profile.equipped, id)
		end
	end
	for _, addon in ADDONS do
		local level = saved.addons and saved.addons[addon.id] or 0
		profile.addons[addon.id] = math.clamp(level, 0, PICKAXES[profile.pickaxe].addonCap)
	end
	return profile, saved
end

local function saveData(player)
	local profile = profiles[player]
	-- Если загрузка не удалась, не сохраняем, чтобы не затереть старый прогресс
	if not store or not profile or not profile.loaded then
		return
	end
	local coins = stat(player, COIN_STAT)
	local rebirths = stat(player, REBIRTH_STAT)
	if not coins or not rebirths then
		return
	end
	local worlds = {}
	for id in profile.worlds do
		table.insert(worlds, id)
	end
	local data = {
		coins = coins.Value,
		level = player:GetAttribute("Level") or 1,
		rebirths = rebirths.Value,
		worlds = worlds,
		pets = profile.pets,
		equipped = profile.equipped,
		pickaxe = profile.pickaxe,
		addons = profile.addons,
		playtime = profile.playtime,
		earned = profile.earned,
		robux = profile.robux,
		petSlots = profile.petSlots,
		auras = (function()
			local list = {}
			for id in profile.auras do
				table.insert(list, id)
			end
			return list
		end)(),
		aura = profile.aura,
		boosts = profile.boosts,
	}
	local ok, err = pcall(function()
		store:SetAsync("player_" .. player.UserId, data)
	end)
	if not ok then
		warn("[DestroySim] Не удалось сохранить " .. player.Name .. ": " .. tostring(err))
	end
end

------------------------------------------------------------------
-- КИРКА В РУКАХ
------------------------------------------------------------------
local PICK_GRIP = CFrame.new(0, -1.3, 0)

local function makePickaxeTool(tier)
	local def = PICKAXES[tier]
	local tool = Instance.new("Tool")
	tool.Name = "Pickaxe"
	tool.ToolTip = def.name
	tool.CanBeDropped = false
	tool.Grip = PICK_GRIP
	tool:SetAttribute("Pickaxe", tier)

	local handle = makePart({
		Name = "Handle",
		Anchored = false,
		CanCollide = false,
		Massless = true,
		Size = V(0.35, 4, 0.35),
		Color = C(110, 80, 45),
		Parent = tool,
	})
	-- головка кирки «ступеньками», как пиксельная
	local head = {
		{ V(0.5, 0.5, 1.0), V(0, 1.8, 0) },
		{ V(0.45, 0.45, 0.7), V(0, 1.65, 0.8) },
		{ V(0.45, 0.45, 0.7), V(0, 1.65, -0.8) },
		{ V(0.4, 0.4, 0.6), V(0, 1.35, 1.35) },
		{ V(0.4, 0.4, 0.6), V(0, 1.35, -1.35) },
	}
	for _, box in head do
		local part = makePart({
			Name = "Head",
			Anchored = false,
			CanCollide = false,
			Massless = true,
			Size = box[1],
			CFrame = handle.CFrame * CFrame.new(box[2]),
			Color = def.color,
			Material = def.neon and NEON or SMOOTH,
			Parent = tool,
		})
		local weld = Instance.new("WeldConstraint")
		weld.Part0 = handle
		weld.Part1 = part
		weld.Parent = part
	end
	return tool
end

local function giveTool(player)
	local profile = profiles[player]
	local character = player.Character
	local backpack = player:FindFirstChildOfClass("Backpack")
	if not profile or not character or not backpack then
		return
	end
	for _, container in { backpack, character } do
		for _, child in container:GetChildren() do
			if child:IsA("Tool") and child:GetAttribute("Pickaxe") then
				child:Destroy()
			end
		end
	end
	local tool = makePickaxeTool(profile.pickaxe)
	tool.Parent = backpack
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	if humanoid then
		humanoid:EquipTool(tool)
	end
end

local function swingTool(player)
	local character = player.Character
	local tool = character and character:FindFirstChild("Pickaxe")
	if tool and tool:IsA("Tool") then
		tool.Grip = PICK_GRIP * CFrame.Angles(math.rad(-60), 0, 0)
		task.delay(0.1, function()
			if tool.Parent then
				tool.Grip = PICK_GRIP
			end
		end)
	end
end

-- Аура: пламя вокруг игрока
local function applyAura(player)
	local profile = profiles[player]
	local character = player.Character
	local root = character and character:FindFirstChild("HumanoidRootPart")
	if not profile or not root then
		return
	end
	for _, name in { "AuraFire", "AuraLight" } do
		local old = root:FindFirstChild(name)
		if old then
			old:Destroy()
		end
	end
	local aura = profile.aura and AURA_BY_ID[profile.aura]
	if not aura then
		return
	end
	local fire = Instance.new("Fire")
	fire.Name = "AuraFire"
	fire.Color = aura.color
	fire.SecondaryColor = aura.secondary
	fire.Size = 6
	fire.Heat = 7
	fire.Parent = root
	local light = Instance.new("PointLight")
	light.Name = "AuraLight"
	light.Color = aura.color
	light.Brightness = 2
	light.Range = 12
	light.Parent = root
end

local function teleport(player, worldId)
	player:SetAttribute("World", worldId)
	local character = player.Character
	if character and character:FindFirstChild("HumanoidRootPart") then
		character:PivotTo(spawnCFrame(worldId))
	end
end

------------------------------------------------------------------
-- ИГРОКИ ЗАХОДЯТ / ВЫХОДЯТ
------------------------------------------------------------------
local lastHit = {}
local combos = {}
local lastAuto = {}
local lastEgg = {}
local trades = {} -- [player] = trade
local tradeRequests = {} -- [target] = { [requester] = время }
local endTrade -- объявлена ниже

local function onPlayerAdded(player)
	player:SetAttribute("World", 1)
	player.CharacterAdded:Connect(function(character)
		task.wait(0.1)
		local world = player:GetAttribute("World") or 1
		if world ~= 1 then
			character:PivotTo(spawnCFrame(world))
		end
		giveTool(player)
		applyAura(player)
	end)

	local profile, saved = loadData(player)
	profiles[player] = profile

	local leaderstats = Instance.new("Folder")
	leaderstats.Name = "leaderstats"

	local coins = Instance.new("IntValue")
	coins.Name = COIN_STAT
	coins.Value = saved.coins or 0
	coins.Parent = leaderstats

	local rebirths = Instance.new("IntValue")
	rebirths.Name = REBIRTH_STAT
	rebirths.Value = saved.rebirths or 0
	rebirths.Parent = leaderstats

	player:SetAttribute("Level", saved.level or 1)
	player:SetAttribute("Combo", 0)
	leaderstats.Parent = player
	refreshPets(player)
	giveTool(player)
end

Players.PlayerAdded:Connect(onPlayerAdded)
for _, player in Players:GetPlayers() do
	task.spawn(onPlayerAdded, player)
end

Players.PlayerRemoving:Connect(function(player)
	local trade = trades[player]
	if trade then
		endTrade(trade, "Игрок вышел — трейд отменён", RED)
	end
	saveData(player)
	profiles[player] = nil
	lastHit[player] = nil
	combos[player] = nil
	lastAuto[player] = nil
	lastEgg[player] = nil
	tradeRequests[player] = nil
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
-- МАГАЗИН: уровень кирки, ребёрт, новые кирки, аддоны
------------------------------------------------------------------
shopRemote.OnServerEvent:Connect(function(player, action, arg)
	local profile = profiles[player]
	local coins = stat(player, COIN_STAT)
	local rebirths = stat(player, REBIRTH_STAT)
	if not profile or not coins or not rebirths then
		return
	end

	if action == "level" or action == "levelMax" then
		local level = player:GetAttribute("Level") or 1
		local bought = 0
		local limit = action == "levelMax" and 1000 or 1
		while bought < limit and coins.Value >= levelCost(level) do
			coins.Value -= levelCost(level)
			level += 1
			bought += 1
		end
		if bought == 0 then
			announce("Не хватает изумрудов!", RED, player)
			return
		end
		player:SetAttribute("Level", level)
		refreshPlayer(player)
		if bought > 1 then
			announce("Уровень кирки +" .. bought .. "!", GOLD, player)
		end
	elseif action == "rebirth" then
		local cost = rebirthCost(rebirths.Value)
		if coins.Value < cost then
			announce("Для ребёрта нужно " .. EM .. " " .. abbreviate(cost), RED, player)
			return
		end
		coins.Value = 0
		rebirths.Value += 1
		player:SetAttribute("Level", 1)
		refreshPlayer(player)
		announce(player.DisplayName .. " сделал ребёрт #" .. rebirths.Value .. "!", C(190, 120, 255))
	elseif action == "pickaxe" then
		local nextTier = profile.pickaxe + 1
		local def = PICKAXES[nextTier]
		if not def then
			return
		end
		if coins.Value < def.price then
			announce("Нужно " .. EM .. " " .. abbreviate(def.price), RED, player)
			return
		end
		coins.Value -= def.price
		profile.pickaxe = nextTier
		refreshPlayer(player)
		giveTool(player)
		announce(player.DisplayName .. " получил: " .. def.name .. "!", def.color)
	elseif action == "petSlot" then
		local nextSlot = profile.petSlots + 1
		local price = PET_SLOT_PRICES[nextSlot]
		if not price then
			return
		end
		if coins.Value < price then
			announce("Нужно " .. EM .. " " .. abbreviate(price), RED, player)
			return
		end
		coins.Value -= price
		profile.petSlots = nextSlot
		refreshPets(player)
		announce("Слотов питомцев: " .. nextSlot .. "!", GREEN, player)
	elseif action == "aura" then
		local aura = typeof(arg) == "string" and AURA_BY_ID[arg]
		if not aura then
			return
		end
		if not profile.auras[aura.id] then
			if coins.Value < aura.price then
				announce("Нужно " .. EM .. " " .. abbreviate(aura.price), RED, player)
				return
			end
			coins.Value -= aura.price
			profile.auras[aura.id] = true
			profile.aura = aura.id
			announce("Аура «" .. aura.name .. "» получена!", aura.color)
		elseif profile.aura == aura.id then
			profile.aura = nil
		else
			profile.aura = aura.id
		end
		applyAura(player)
		refreshPlayer(player)
	elseif action == "potion" then
		local potion = typeof(arg) == "string" and POTION_BY_ID[arg]
		if not potion then
			return
		end
		local price = math.max(potion.base, math.floor(levelCost(player:GetAttribute("Level") or 1) * potion.mult))
		if coins.Value < price then
			announce("Нужно " .. EM .. " " .. abbreviate(price), RED, player)
			return
		end
		coins.Value -= price
		profile.boosts[potion.id] = math.max(os.time(), profile.boosts[potion.id] or 0) + potion.minutes * 60
		refreshPlayer(player)
		announce(potion.name .. " выпито!", potion.color, player)
	elseif action == "addon" then
		local addon = typeof(arg) == "string" and ADDON_BY_ID[arg]
		if not addon then
			return
		end
		local level = profile.addons[addon.id] or 0
		if level >= PICKAXES[profile.pickaxe].addonCap then
			announce("Нужна кирка получше, чтобы прокачать дальше", RED, player)
			return
		end
		local cost = addonCost(addon, level)
		if coins.Value < cost then
			announce("Нужно " .. EM .. " " .. abbreviate(cost), RED, player)
			return
		end
		coins.Value -= cost
		profile.addons[addon.id] = level + 1
		refreshPlayer(player)
		announce(addon.name .. " → ур. " .. (level + 1), GOLD, player)
	end
end)

-- Следим за зельями: когда действие кончается, обновляем урон и множитель
local boostState = {}
task.spawn(function()
	while true do
		task.wait(1)
		for _, player in Players:GetPlayers() do
			local profile = profiles[player]
			if profile then
				local state = boostState[player] or {}
				boostState[player] = state
				local changed = false
				for _, potion in POTIONS do
					local active = boostActive(player, potion.id)
					if state[potion.id] ~= active then
						state[potion.id] = active
						changed = true
					end
				end
				if changed then
					refreshPlayer(player)
				end
				local humanoid = player.Character and player.Character:FindFirstChildOfClass("Humanoid")
				local speed = state.speed and 30 or 16
				if humanoid and humanoid.WalkSpeed ~= speed then
					humanoid.WalkSpeed = speed
				end
			end
		end
	end
end)
Players.PlayerRemoving:Connect(function(player)
	boostState[player] = nil
end)

------------------------------------------------------------------
-- МИРЫ
------------------------------------------------------------------
goToWorld = function(player, worldId)
	local world = WORLDS[worldId]
	local profile = profiles[player]
	local coins = stat(player, COIN_STAT)
	if not world or not profile or not coins then
		return
	end
	if not profile.worlds[worldId] then
		if coins.Value < world.price then
			announce("Мир «" .. world.name .. "» стоит " .. EM .. " " .. abbreviate(world.price), RED, player)
			return
		end
		coins.Value -= world.price
		profile.worlds[worldId] = true
		refreshPlayer(player)
		announce(player.DisplayName .. " открыл мир «" .. world.name .. "»!", world.accent)
	end
	teleport(player, worldId)
end

worldRemote.OnServerEvent:Connect(function(player, worldId)
	if typeof(worldId) == "number" then
		goToWorld(player, worldId)
	end
end)

------------------------------------------------------------------
-- ПИТОМЦЫ И ЯЙЦА
------------------------------------------------------------------
openEgg = function(player, worldId)
	local world = WORLDS[worldId]
	local profile = profiles[player]
	local coins = stat(player, COIN_STAT)
	if not world or not profile or not coins then
		return
	end
	local now = os.clock()
	if lastEgg[player] and now - lastEgg[player] < 1 then
		return
	end
	if not profile.worlds[worldId] then
		announce("Сначала открой мир «" .. world.name .. "»", RED, player)
		return
	end
	if #profile.pets >= CONFIG.MaxPets then
		announce("Инвентарь питомцев полон! Удали лишних", RED, player)
		return
	end
	if coins.Value < world.egg.price then
		announce("Яйцо стоит " .. EM .. " " .. abbreviate(world.egg.price), RED, player)
		return
	end
	lastEgg[player] = now
	coins.Value -= world.egg.price

	local total = 0
	for _, entry in world.egg.pets do
		total += entry.weight
	end
	local roll = math.random() * total
	local kind = world.egg.pets[1].kind
	for _, entry in world.egg.pets do
		roll -= entry.weight
		if roll <= 0 then
			kind = entry.kind
			break
		end
	end

	local pet = { id = HttpService:GenerateGUID(false), kind = kind }
	table.insert(profile.pets, pet)
	if #profile.equipped < petSlots(player) then
		table.insert(profile.equipped, pet.id)
	end
	refreshPets(player)
	eggResultRemote:FireClient(player, kind, world.id)

	local rarity = PETS[kind].rarity
	if rarity == "Легендарный" or rarity == "Мифический" then
		announce(player.DisplayName .. " выбил питомца «" .. kind .. "» (" .. rarity .. ")!", RARITIES[rarity].color)
	end
end

openEggRemote.OnServerEvent:Connect(function(player, worldId)
	if typeof(worldId) == "number" then
		openEgg(player, worldId)
	end
end)

petRemote.OnServerEvent:Connect(function(player, action, id)
	local profile = profiles[player]
	if not profile then
		return
	end
	if action == "sync" then
		sendInventory(player)
		return
	end
	if action == "best" then
		local sorted = table.clone(profile.pets)
		table.sort(sorted, function(a, b)
			return PETS[a.kind].bonus > PETS[b.kind].bonus
		end)
		profile.equipped = {}
		for i = 1, math.min(petSlots(player), #sorted) do
			table.insert(profile.equipped, sorted[i].id)
		end
		refreshPets(player)
		return
	end

	if typeof(id) ~= "string" or not findPet(profile, id) then
		return
	end
	local equippedIndex = table.find(profile.equipped, id)
	if action == "equip" and not equippedIndex then
		if #profile.equipped >= petSlots(player) then
			announce("Открой больше слотов у Крипера! Сейчас: " .. petSlots(player), RED, player)
			return
		end
		table.insert(profile.equipped, id)
	elseif action == "unequip" and equippedIndex then
		table.remove(profile.equipped, equippedIndex)
	elseif action == "delete" then
		local trade = trades[player]
		if trade and table.find(trade.offers[player], id) then
			announce("Этот питомец сейчас в трейде", RED, player)
			return
		end
		local _, index = findPet(profile, id)
		table.remove(profile.pets, index)
		if equippedIndex then
			table.remove(profile.equipped, equippedIndex)
		end
	end
	refreshPets(player)
end)

------------------------------------------------------------------
-- ТРЕЙДЫ
------------------------------------------------------------------
local function petsPayload(owner, ids)
	local profile = profiles[owner]
	local list = {}
	if profile then
		for _, id in ids do
			local pet = findPet(profile, id)
			if pet then
				table.insert(list, { id = pet.id, kind = pet.kind })
			end
		end
	end
	return list
end

local function sendTradeState(trade)
	for _, p in { trade.a, trade.b } do
		local other = p == trade.a and trade.b or trade.a
		tradeRemote:FireClient(p, "state", {
			partner = other.DisplayName,
			mine = petsPayload(p, trade.offers[p]),
			theirs = petsPayload(other, trade.offers[other]),
			myReady = trade.ready[p] == true,
			theirReady = trade.ready[other] == true,
			countdown = trade.countdownEnd ~= nil,
		})
	end
end

endTrade = function(trade, message, color)
	trade.closed = true
	for _, p in { trade.a, trade.b } do
		if trades[p] == trade then
			trades[p] = nil
		end
		if p.Parent then
			tradeRemote:FireClient(p, "closed")
			announce(message, color, p)
		end
	end
end

local function resetReady(trade)
	trade.ready = {}
	trade.countdownEnd = nil
	trade.token += 1
end

local function executeTrade(trade)
	local a, b = trade.a, trade.b
	local pa, pb = profiles[a], profiles[b]
	if not pa or not pb then
		endTrade(trade, "Трейд отменён", RED)
		return
	end
	for _, side in { { pa, trade.offers[a] }, { pb, trade.offers[b] } } do
		for _, id in side[2] do
			if not findPet(side[1], id) then
				endTrade(trade, "Питомец пропал — трейд отменён", RED)
				return
			end
		end
	end
	if
		#pa.pets - #trade.offers[a] + #trade.offers[b] > CONFIG.MaxPets
		or #pb.pets - #trade.offers[b] + #trade.offers[a] > CONFIG.MaxPets
	then
		endTrade(trade, "У кого-то не хватает места для питомцев", RED)
		return
	end

	local function move(from, to, ids)
		for _, id in ids do
			local pet, index = findPet(from, id)
			if pet then
				table.remove(from.pets, index)
				local equippedIndex = table.find(from.equipped, id)
				if equippedIndex then
					table.remove(from.equipped, equippedIndex)
				end
				table.insert(to.pets, pet)
			end
		end
	end
	move(pa, pb, trade.offers[a])
	move(pb, pa, trade.offers[b])
	refreshPets(a)
	refreshPets(b)
	task.spawn(saveData, a)
	task.spawn(saveData, b)
	endTrade(trade, "Трейд завершён!", GREEN)
end

tradeRemote.OnServerEvent:Connect(function(player, action, arg)
	if action == "request" then
		local target = typeof(arg) == "number" and Players:GetPlayerByUserId(arg)
		if not target or target == player or not profiles[target] then
			return
		end
		if trades[player] or trades[target] then
			announce("Игрок уже в трейде", RED, player)
			return
		end
		tradeRequests[target] = tradeRequests[target] or {}
		tradeRequests[target][player] = os.clock()
		tradeRemote:FireClient(target, "request", { userId = player.UserId, name = player.DisplayName })
		announce("Запрос отправлен: " .. target.DisplayName, WHITE, player)
		return
	end

	if action == "accept" or action == "decline" then
		local from = typeof(arg) == "number" and Players:GetPlayerByUserId(arg)
		local pending = tradeRequests[player]
		local sentAt = pending and from and pending[from]
		if pending and from then
			pending[from] = nil
		end
		if not from or not sentAt or os.clock() - sentAt > 60 then
			announce("Запрос на трейд устарел", RED, player)
			return
		end
		if action == "decline" then
			announce(player.DisplayName .. " отклонил трейд", RED, from)
			return
		end
		if trades[player] or trades[from] then
			announce("Кто-то из вас уже в трейде", RED, player)
			return
		end
		local trade = { a = from, b = player, offers = { [from] = {}, [player] = {} }, ready = {}, token = 0 }
		trades[from] = trade
		trades[player] = trade
		sendTradeState(trade)
		return
	end

	local trade = trades[player]
	local profile = profiles[player]
	if not trade or not profile then
		return
	end
	local offer = trade.offers[player]

	if action == "add" then
		if typeof(arg) ~= "string" or not findPet(profile, arg) or table.find(offer, arg) then
			return
		end
		if #offer >= CONFIG.TradeMaxPets then
			announce("Максимум " .. CONFIG.TradeMaxPets .. " питомцев за трейд", RED, player)
			return
		end
		table.insert(offer, arg)
		resetReady(trade)
		sendTradeState(trade)
	elseif action == "remove" then
		local index = table.find(offer, arg)
		if index then
			table.remove(offer, index)
			resetReady(trade)
			sendTradeState(trade)
		end
	elseif action == "ready" then
		trade.ready[player] = not trade.ready[player]
		trade.countdownEnd = nil
		trade.token += 1
		if trade.ready[trade.a] and trade.ready[trade.b] then
			trade.countdownEnd = os.clock() + CONFIG.TradeCountdown
			local token = trade.token
			task.delay(CONFIG.TradeCountdown, function()
				if not trade.closed and trade.token == token then
					executeTrade(trade)
				end
			end)
		end
		sendTradeState(trade)
	elseif action == "cancel" then
		endTrade(trade, "Трейд отменён", RED)
	end
end)

------------------------------------------------------------------
-- БЛОКИ
------------------------------------------------------------------
local eventActive = false
local blockData = {} -- [Model] = { hp, maxHp, def, world, key, model, core, damageBy, fill, hpText, scale }
local occupied = {} -- [worldId] = { ["i,j"] = true }
local blockCount = {} -- [worldId] = число
for _, world in WORLDS do
	occupied[world.id] = {}
	blockCount[world.id] = 0
end

local function pickBlock(world)
	local boost = eventActive and CONFIG.EventRareBoost or 1
	local total = 0
	for _, def in world.blocks do
		total += def.weight * (def.rare and boost or 1)
	end
	local roll = math.random() * total
	for _, def in world.blocks do
		roll -= def.weight * (def.rare and boost or 1)
		if roll <= 0 then
			return def
		end
	end
	return world.blocks[1]
end

local function freeSpot(worldId)
	local spots = spawnSpots[worldId]
	if not spots or #spots == 0 then
		return nil
	end
	for _ = 1, 30 do
		local spot = spots[math.random(1, #spots)]
		if not occupied[worldId][spot.key] then
			return spot
		end
	end
	return nil
end

local function updateBar(data)
	local ratio = math.clamp(data.hp / data.maxHp, 0, 1)
	data.fill.Size = UDim2.fromScale(ratio, 1)
	data.fill.BackgroundColor3 = C(255, 70, 70):Lerp(C(70, 230, 100), ratio)
	data.hpText.Text = abbreviate(data.hp) .. " / " .. abbreviate(data.maxHp)
end

local function makeHealthBar(core, def)
	local gui = Instance.new("BillboardGui")
	gui.Name = "HealthBar"
	gui.Size = UDim2.fromOffset(110, 36)
	gui.StudsOffset = V(0, core.Size.Y / 2 + 1.8, 0)
	-- обычные блоки подписаны только вблизи, чтобы надписи не налезали друг на друга
	gui.MaxDistance = def.rare and 120 or 32
	gui.LightInfluence = 0

	local title = Instance.new("TextLabel")
	title.BackgroundTransparency = 1
	title.Size = UDim2.fromScale(1, 0.5)
	title.Font = Enum.Font.GothamBlack
	title.TextScaled = true
	title.TextColor3 = def.rare and (def.spot or def.color) or WHITE
	title.Text = def.name
	title.Parent = gui
	local titleStroke = Instance.new("UIStroke")
	titleStroke.Thickness = 1.5
	titleStroke.Color = C(15, 15, 25)
	titleStroke.Parent = title

	local back = Instance.new("Frame")
	back.Position = UDim2.fromScale(0, 0.56)
	back.Size = UDim2.fromScale(1, 0.44)
	back.BackgroundColor3 = C(25, 25, 35)
	back.BorderSizePixel = 0
	back.Parent = gui
	Instance.new("UICorner").Parent = back
	local backStroke = Instance.new("UIStroke")
	backStroke.Thickness = 2
	backStroke.Color = C(15, 15, 25)
	backStroke.Parent = back

	local fill = Instance.new("Frame")
	fill.Size = UDim2.fromScale(1, 1)
	fill.BorderSizePixel = 0
	fill.Parent = back
	Instance.new("UICorner").Parent = fill
	local shine = Instance.new("UIGradient")
	shine.Color = ColorSequence.new(WHITE, C(170, 170, 170))
	shine.Rotation = 90
	shine.Parent = fill

	local hpText = Instance.new("TextLabel")
	hpText.BackgroundTransparency = 1
	hpText.Size = UDim2.fromScale(1, 1)
	hpText.ZIndex = 2
	hpText.Font = Enum.Font.GothamBold
	hpText.TextScaled = true
	hpText.TextColor3 = WHITE
	hpText.TextStrokeTransparency = 0.2
	hpText.Parent = back

	gui.Parent = core
	return fill, hpText
end

-- Плавно меняет размер блока (появление и «сжатие» от удара)
local function animateScale(data, from, duration)
	data.scale.Value = from
	TweenService:Create(data.scale, TweenInfo.new(duration, Enum.EasingStyle.Back, Enum.EasingDirection.Out), {
		Value = 1,
	}):Play()
end

local function destroyBlock(data)
	data.dead = true
	blockData[data.model] = nil
	occupied[data.world][data.key] = nil
	blockCount[data.world] -= 1

	local totalDamage = 0
	for _, damage in data.damageBy do
		totalDamage += damage
	end

	local now = os.clock()
	local eventMult = eventActive and CONFIG.EventCoinMultiplier or 1
	local topPlayer, topReward = nil, 0
	for player, damage in data.damageBy do
		if player.Parent and profiles[player] then
			-- Награда делится между всеми, кто бил блок, по нанесённому урону
			local combo = combos[player]
			local comboCount = (combo and now - combo.last <= CONFIG.ComboWindow) and combo.count or 0
			local comboMult = 1 + math.min(comboCount, CONFIG.ComboMax) / CONFIG.ComboMax
			local reward = data.def.reward * (damage / totalDamage) * coinMultiplier(player) * comboMult * eventMult
			reward = math.max(1, math.floor(reward))
			local coins = stat(player, COIN_STAT)
			if coins then
				coins.Value += reward
				profiles[player].earned += reward
			end
			if reward > topReward then
				topPlayer, topReward = player, reward
			end
		end
	end

	if topPlayer then
		popup(data.core.Position + V(0, data.core.Size.Y / 2, 0), "+" .. abbreviate(topReward) .. " " .. EM, GOLD, true)
		if data.def.announce then
			announce(
				topPlayer.DisplayName .. " разбил «" .. data.def.name .. "» и получил " .. EM .. " " .. abbreviate(topReward) .. "!",
				data.def.spot or data.def.color
			)
		end
	end

	shatter(data.core, data.def)
	data.model:Destroy()
end

local function applyDamage(player, data, damage, crit)
	if data.dead then
		return
	end
	local dealt = math.min(damage, data.hp)
	data.hp -= dealt
	data.damageBy[player] = (data.damageBy[player] or 0) + dealt

	local s = data.core.Size.Y
	local jitter = V((math.random() - 0.5) * 3, s / 2, (math.random() - 0.5) * 3)
	if crit then
		popup(data.core.Position + jitter, "КРИТ! -" .. abbreviate(damage), C(255, 120, 40), true)
	else
		popup(data.core.Position + jitter, "-" .. abbreviate(damage), WHITE, false)
	end

	if data.hp <= 0 then
		destroyBlock(data)
	else
		updateBar(data)
		animateScale(data, 0.88, 0.15)
	end
end

local function hitBlock(player, model, auto)
	local data = blockData[model]
	if not data or data.dead or not profiles[player] then
		return
	end
	if player:GetAttribute("World") ~= data.world then
		return
	end

	local now = os.clock()
	if not auto then
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
	end

	local critChance = CONFIG.CritChance + addonLevel(player, "sharpness") * 0.03
	local crit = math.random() < critChance
	local damage = damageOf(player) * (crit and CONFIG.CritMultiplier or 1)
	swingTool(player)
	applyDamage(player, data, damage, crit)

	-- Аддон «Взрыв»: бьёт все блоки вокруг
	local blast = addonLevel(player, "blast")
	if blast > 0 and math.random() < blast * 0.08 then
		local center = data.core.Position
		local explosion = Instance.new("Explosion")
		explosion.BlastPressure = 0
		explosion.DestroyJointRadiusPercent = 0
		explosion.BlastRadius = 12
		explosion.Position = center
		explosion.Parent = effectsFolder

		local targets = {}
		for _, other in blockData do
			if other ~= data and other.world == data.world and (other.core.Position - center).Magnitude <= 14 then
				table.insert(targets, other)
			end
		end
		for _, other in targets do
			applyDamage(player, other, math.max(1, math.floor(damage * 0.5)), false)
		end
	end
end

local function spawnBlock(world, def)
	local spot = freeSpot(world.id)
	if not spot then
		return
	end
	local key = spot.key
	local s = def.size or CONFIG.BlockSize
	local position = V(spot.x, s / 2, spot.z)
	local model, core = buildBlockModel(def, position)
	local fill, hpText = makeHealthBar(core, def)

	local click = Instance.new("ClickDetector")
	click.MaxActivationDistance = CONFIG.ClickDistance
	click.Parent = model

	local scale = Instance.new("NumberValue")
	scale.Name = "Scale"
	scale.Value = 1
	scale.Parent = model
	scale.Changed:Connect(function(value)
		if model.Parent and value > 0.05 then
			model:ScaleTo(value)
		end
	end)

	local data = {
		hp = def.hp,
		maxHp = def.hp,
		def = def,
		world = world.id,
		key = key,
		model = model,
		core = core,
		damageBy = {},
		fill = fill,
		hpText = hpText,
		scale = scale,
	}
	occupied[world.id][key] = true
	blockCount[world.id] += 1
	blockData[model] = data
	updateBar(data)

	click.MouseClick:Connect(function(player)
		hitBlock(player, model, false)
	end)

	model.Parent = blocksFolder
	animateScale(data, 0.2, 0.35)

	if def.announce then
		announce("В мире «" .. world.name .. "» появилась " .. def.name .. "!", def.spot or def.color)
	end
end

-- Удар киркой (клик с инструментом в руках)
hitRemote.OnServerEvent:Connect(function(player, model)
	if typeof(model) ~= "Instance" then
		return
	end
	local data = blockData[model]
	local character = player.Character
	local root = character and character:FindFirstChild("HumanoidRootPart")
	if not data or not root or (root.Position - data.core.Position).Magnitude > CONFIG.ClickDistance + 5 then
		return
	end
	hitBlock(player, model, false)
end)

-- Аддон «Автокопка»
task.spawn(function()
	while true do
		task.wait(0.1)
		local now = os.clock()
		for _, player in Players:GetPlayers() do
			local level = addonLevel(player, "auto")
			if level > 0 and now - (lastAuto[player] or 0) >= math.max(0.2, 2.2 - level * 0.2) then
				local character = player.Character
				local root = character and character:FindFirstChild("HumanoidRootPart")
				local world = player:GetAttribute("World")
				if root then
					local best, bestDistance = nil, 22
					for model, data in blockData do
						if data.world == world then
							local distance = (data.core.Position - root.Position).Magnitude
							if distance < bestDistance then
								best, bestDistance = model, distance
							end
						end
					end
					if best then
						lastAuto[player] = now
						hitBlock(player, best, true)
					end
				end
			end
		end
	end
end)

------------------------------------------------------------------
-- МОДЕЛИ ПИТОМЦЕВ (клиент копирует их, чтобы показать)
------------------------------------------------------------------
local petModels = Instance.new("Folder")
petModels.Name = "PetModels"
for kind, def in PETS do
	local model = Instance.new("Model")
	model.Name = kind
	for index, box in def.parts do
		local part = makePart({
			Name = index == 1 and "Root" or "Part",
			Size = box[1],
			CFrame = CFrame.new(box[2]),
			Color = box[3],
			Material = box[4] or SMOOTH,
			CanCollide = false,
			CanQuery = false,
			CanTouch = false,
			Parent = model,
		})
		if index == 1 then
			model.PrimaryPart = part
		end
	end
	model.Parent = petModels
end
petModels.Parent = ReplicatedStorage

------------------------------------------------------------------
-- 3D-ИКОНКИ ДЛЯ ИНТЕРФЕЙСА (кирки, блоки миров, яйца)
------------------------------------------------------------------
local icons = Instance.new("Folder")
icons.Name = "Icons"
for tier in PICKAXES do
	local tool = makePickaxeTool(tier)
	local model = Instance.new("Model")
	model.Name = "Pickaxe_" .. tier
	for _, part in tool:GetChildren() do
		if part:IsA("BasePart") then
			part.Anchored = true
			part.Parent = model
		end
	end
	model.PrimaryPart = model:FindFirstChild("Handle") :: BasePart
	tool:Destroy()
	model.Parent = icons
end
for _, world in WORLDS do
	local block = buildBlockModel(world.blocks[1], V(0, 0, 0))
	block.Name = "Block_" .. world.id
	block.Parent = icons

	local egg = Instance.new("Model")
	egg.Name = "Egg_" .. world.id
	makePart({ Size = V(4, 2, 4), Position = V(0, -2.5, 0), Color = world.egg.color, Parent = egg })
	local middle = makePart({ Size = V(5, 3, 5), Position = V(0, 0, 0), Color = world.egg.color, Parent = egg })
	makePart({ Size = V(3.5, 2, 3.5), Position = V(0, 2.5, 0), Color = world.egg.color, Parent = egg })
	addSpots(egg, middle, { world.egg.spot }, 3, { sidesOnly = true })
	egg.PrimaryPart = middle
	egg.Parent = icons
end
icons.Parent = ReplicatedStorage

------------------------------------------------------------------
-- ИНФОРМАЦИЯ ДЛЯ ИНТЕРФЕЙСА
------------------------------------------------------------------
local GAME_INFO = {
	worlds = {},
	pets = {},
	rarities = RARITIES,
	pickaxes = {},
	addons = {},
	maxEquipped = MAX_PET_SLOTS,
	basePetSlots = BASE_PET_SLOTS,
	petSlotPrices = {},
	auras = {},
	potions = {},
	maxPets = CONFIG.MaxPets,
	comboWindow = CONFIG.ComboWindow,
	tradeCountdown = CONFIG.TradeCountdown,
	rebirthBonus = CONFIG.RebirthBonus,
}
for _, world in WORLDS do
	local total = 0
	for _, entry in world.egg.pets do
		total += entry.weight
	end
	local eggPets = {}
	for _, entry in world.egg.pets do
		table.insert(eggPets, { kind = entry.kind, chance = entry.weight / total * 100 })
	end
	table.insert(GAME_INFO.worlds, {
		id = world.id,
		name = world.name,
		price = world.price,
		accent = world.accent,
		egg = { name = world.egg.name, price = world.egg.price, pets = eggPets },
		eggColor = world.egg.color,
		eggSpot = world.egg.spot,
	})
end
for slot = BASE_PET_SLOTS + 1, MAX_PET_SLOTS do
	table.insert(GAME_INFO.petSlotPrices, { slot = slot, price = PET_SLOT_PRICES[slot] })
end
for _, aura in AURAS do
	table.insert(GAME_INFO.auras, { id = aura.id, name = aura.name, boost = aura.boost, price = aura.price, color = aura.color, secondary = aura.secondary })
end
for _, potion in POTIONS do
	table.insert(GAME_INFO.potions, { id = potion.id, name = potion.name, desc = potion.desc, base = potion.base, mult = potion.mult, color = potion.color })
end
for kind, def in PETS do
	GAME_INFO.pets[kind] = { rarity = def.rarity, bonus = def.bonus, fly = def.fly == true }
end
for _, def in PICKAXES do
	table.insert(GAME_INFO.pickaxes, { name = def.name, damage = def.damage, price = def.price, addonCap = def.addonCap, color = def.color })
end
for _, addon in ADDONS do
	table.insert(GAME_INFO.addons, { id = addon.id, name = addon.name, desc = addon.desc, baseCost = addon.baseCost, growth = addon.growth })
end

infoRemote.OnServerInvoke = function()
	return GAME_INFO
end

remotes.Parent = ReplicatedStorage

------------------------------------------------------------------
-- ЗАПУСК МИРОВ
------------------------------------------------------------------
for _, world in WORLDS do
	buildWorld(world)
	for _ = 1, CONFIG.MaxBlocksPerWorld do
		spawnBlock(world, pickBlock(world))
	end
end

task.spawn(function()
	while true do
		task.wait(CONFIG.SpawnInterval)
		for _, world in WORLDS do
			if blockCount[world.id] < CONFIG.MaxBlocksPerWorld then
				spawnBlock(world, pickBlock(world))
			end
		end
	end
end)

------------------------------------------------------------------
-- ЗОЛОТАЯ ЛИХОРАДКА
------------------------------------------------------------------
task.spawn(function()
	while true do
		workspace:SetAttribute("EventActive", false)
		workspace:SetAttribute("NextEventAt", workspace:GetServerTimeNow() + CONFIG.EventEvery)
		task.wait(CONFIG.EventEvery)

		eventActive = true
		workspace:SetAttribute("EventActive", true)
		workspace:SetAttribute("EventMultiplier", CONFIG.EventCoinMultiplier)
		workspace:SetAttribute("EventEndsAt", workspace:GetServerTimeNow() + CONFIG.EventDuration)
		announce("ЗОЛОТАЯ ЛИХОРАДКА! Изумруды x" .. CONFIG.EventCoinMultiplier .. "!", GOLD)

		for _ = 1, 3 do
			for _, world in WORLDS do
				spawnBlock(world, world.blocks[world.eventBlock])
			end
			task.wait(0.3)
		end

		task.wait(CONFIG.EventDuration)
		eventActive = false
		announce("Лихорадка закончилась. Копи силу до следующей!", WHITE)
	end
end)

------------------------------------------------------------------
-- АДМИН-ПАНЕЛЬ
-- Админы: владелец игры, все в Studio и UserId из списка ниже.
-- Свой UserId можно узнать в адресе своего профиля на roblox.com
------------------------------------------------------------------
local ADMINS = {
	-- 123456789,
}

local function isAdmin(player)
	return RunService:IsStudio() or player.UserId == game.CreatorId or table.find(ADMINS, player.UserId) ~= nil
end

local function markAdmin(player)
	player:SetAttribute("IsAdmin", isAdmin(player))
end
Players.PlayerAdded:Connect(markAdmin)
for _, player in Players:GetPlayers() do
	markAdmin(player)
end

adminRemote.OnServerEvent:Connect(function(player, action, arg)
	local profile = profiles[player]
	local coins = stat(player, COIN_STAT)
	local rebirths = stat(player, REBIRTH_STAT)
	if not isAdmin(player) or not profile or not coins or not rebirths then
		return
	end
	if action == "coins" and typeof(arg) == "number" then
		coins.Value += math.clamp(math.floor(arg), 0, 1e15)
	elseif action == "resetCoins" then
		coins.Value = 0
	elseif action == "rebirth" then
		rebirths.Value += 1
	elseif action == "level" then
		player:SetAttribute("Level", (player:GetAttribute("Level") or 1) + 10)
	elseif action == "worlds" then
		for _, world in WORLDS do
			profile.worlds[world.id] = true
		end
	elseif action == "pickaxe" then
		profile.pickaxe = #PICKAXES
		for _, addon in ADDONS do
			profile.addons[addon.id] = PICKAXES[#PICKAXES].addonCap
		end
		giveTool(player)
	elseif action == "pet" and typeof(arg) == "string" and PETS[arg] then
		if #profile.pets >= CONFIG.MaxPets then
			announce("Инвентарь питомцев полон", RED, player)
			return
		end
		table.insert(profile.pets, { id = HttpService:GenerateGUID(false), kind = arg })
	else
		return
	end
	refreshPets(player)
	announce("Готово!", GREEN, player)
end)

------------------------------------------------------------------
-- ПОКУПКИ ЗА ROBUX
-- Сюда можно добавить свои товары (Developer Products):
-- [ID товара] = function(player) ... что выдать ... end
------------------------------------------------------------------
local PRODUCTS = {}

MarketplaceService.ProcessReceipt = function(receipt)
	local player = Players:GetPlayerByUserId(receipt.PlayerId)
	local profile = player and profiles[player]
	if not player or not profile then
		return Enum.ProductPurchaseDecision.NotProcessedYet
	end
	local handler = PRODUCTS[receipt.ProductId]
	if handler then
		local ok, err = pcall(handler, player)
		if not ok then
			warn("[DestroySim] Ошибка выдачи товара: " .. tostring(err))
			return Enum.ProductPurchaseDecision.NotProcessedYet
		end
	end
	profile.robux += receipt.CurrencySpent
	task.spawn(saveData, player)
	return Enum.ProductPurchaseDecision.PurchaseGranted
end

MarketplaceService.PromptGamePassPurchaseFinished:Connect(function(player, passId, purchased)
	local profile = profiles[player]
	if not purchased or not profile then
		return
	end
	local ok, info = pcall(function()
		return MarketplaceService:GetProductInfo(passId, Enum.InfoType.GamePass)
	end)
	if ok and info and info.PriceInRobux then
		profile.robux += info.PriceInRobux
	end
end)

------------------------------------------------------------------
-- ТАБЛИЦЫ РЕКОРДОВ (стоят в мире «Луга» за спавном)
------------------------------------------------------------------
local function formatTime(seconds)
	local minutes = math.floor(seconds / 60)
	local hours = math.floor(minutes / 60)
	local days = math.floor(hours / 24)
	if days > 0 then
		return days .. "д " .. hours % 24 .. "ч"
	elseif hours > 0 then
		return hours .. "ч " .. minutes % 60 .. "м"
	end
	return minutes .. "м"
end

local BOARDS = {
	{
		id = "time",
		title = "Время в игре",
		icon = "clock",
		color = C(80, 160, 255),
		value = function(_, profile)
			return profile.playtime
		end,
		format = formatTime,
	},
	{
		id = "coins",
		title = "Больше всего изумрудов",
		icon = "emerald",
		color = C(255, 190, 30),
		value = function(_, profile)
			return profile.earned
		end,
		format = abbreviate,
	},
	{
		id = "rebirths",
		title = "Ребёрты",
		icon = "rebirth",
		color = C(190, 100, 255),
		value = function(player)
			local rebirths = stat(player, REBIRTH_STAT)
			return rebirths and rebirths.Value or 0
		end,
		format = abbreviate,
	},
	{
		id = "robux",
		title = "Потрачено Robux",
		icon = "gem",
		color = C(70, 210, 90),
		value = function(_, profile)
			return profile.robux
		end,
		format = function(n)
			return abbreviate(n) .. " R$"
		end,
	},
}

local RANK_COLORS = { C(235, 180, 30), C(165, 172, 190), C(200, 115, 55) }

local function uiStroke(parent, thickness)
	local stroke = Instance.new("UIStroke")
	stroke.Thickness = thickness
	stroke.Color = C(15, 15, 20)
	stroke.ApplyStrokeMode = Enum.ApplyStrokeMode.Border
	stroke.Parent = parent
	return stroke
end

local function boardText(parent, props, strokeThickness)
	local text = Instance.new("TextLabel")
	text.BackgroundTransparency = 1
	text.Font = Enum.Font.GothamBlack
	text.TextScaled = true
	text.TextColor3 = WHITE
	for key, value in props do
		text[key] = value
	end
	local stroke = Instance.new("UIStroke")
	stroke.Thickness = strokeThickness or 3
	stroke.Color = C(15, 15, 20)
	stroke.Parent = text
	text.Parent = props.Parent
	return text
end

local function buildBoard(board, groundCFrame)
	local model = Instance.new("Model")
	model.Name = "Leaderboard_" .. board.id
	local frame = makePart({
		Name = "Leaderboard_" .. board.id,
		Size = V(20, 26, 1.2),
		Position = V(0, 28, 0),
		Color = C(50, 34, 20),
		Parent = model,
	})
	-- деревянная рамка, как у доски в Майнкрафте
	local woodFrame = C(125, 85, 42)
	for _, edge in {
		{ V(22, 1.2, 1.8), V(0, 13.6, 0) },
		{ V(22, 1.2, 1.8), V(0, -13.6, 0) },
		{ V(1.2, 28.4, 1.8), V(10.6, 0, 0) },
		{ V(1.2, 28.4, 1.8), V(-10.6, 0, 0) },
	} do
		local part = makePart({ Name = "Frame", Size = edge[1], Position = V(0, 28, 0) + edge[2], Color = woodFrame, Parent = model })
		addSpots(model, part, { woodFrame:Lerp(DARK, 0.25), woodFrame:Lerp(WHITE, 0.12) }, 3)
	end
	-- ножки
	for _, x in { -8, 8 } do
		makePart({ Name = "Leg", Size = V(1.4, 14, 1.4), Position = V(x, 7, 0.6), Color = woodFrame:Lerp(DARK, 0.2), Parent = model })
	end
	-- золотой пьедестал для игрока №1
	local podium = makePart({ Name = "Podium", Size = V(6, 3, 6), Position = V(0, 1.5, -8), Color = C(255, 200, 40), Parent = model })
	addSpots(model, podium, { C(230, 165, 20), C(255, 235, 120) }, 4)
	local podiumGui = Instance.new("SurfaceGui")
	podiumGui.Face = Enum.NormalId.Front
	podiumGui.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
	podiumGui.PixelsPerStud = 30
	podiumGui.LightInfluence = 0
	podiumGui.Parent = podium
	boardText(podiumGui, { Size = UDim2.fromScale(1, 1), Text = "1", Parent = podiumGui }, 5)

	local surface = Instance.new("SurfaceGui")
	surface.Face = Enum.NormalId.Front
	surface.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
	surface.PixelsPerStud = 30
	surface.LightInfluence = 0
	surface.Parent = frame

	local background = Instance.new("Frame")
	background.Size = UDim2.fromScale(1, 1)
	background.BackgroundColor3 = C(42, 30, 20)
	background.BorderSizePixel = 0
	background.Parent = surface
	-- пиксельная текстура тёмного дерева
	for _ = 1, 220 do
		local pixel = Instance.new("Frame")
		pixel.BorderSizePixel = 0
		pixel.BackgroundColor3 = math.random() < 0.5 and C(0, 0, 0) or C(255, 220, 160)
		pixel.BackgroundTransparency = 0.9
		pixel.Size = UDim2.fromScale(1 / 20, 1 / 26)
		pixel.Position = UDim2.fromScale(math.random(0, 19) / 20, math.random(0, 25) / 26)
		pixel.Parent = background
	end

	local header = Instance.new("Frame")
	header.Position = UDim2.fromOffset(16, 16)
	header.Size = UDim2.new(1, -32, 0, 110)
	header.BackgroundColor3 = board.color
	header.BorderSizePixel = 0
	header.Parent = surface
	Instance.new("UICorner").Parent = header
	uiStroke(header, 6)
	local shine = Instance.new("UIGradient")
	shine.Color = ColorSequence.new(WHITE, C(170, 170, 180))
	shine.Rotation = 90
	shine.Parent = header

	-- сюда клиент вставит пиксельную иконку
	local slot = Instance.new("Frame")
	slot.Name = "PixelIconSlot"
	slot.BackgroundTransparency = 1
	slot.Position = UDim2.fromOffset(14, 12)
	slot.Size = UDim2.fromOffset(86, 86)
	slot:SetAttribute("Icon", board.icon)
	slot.Parent = header

	boardText(header, {
		Position = UDim2.fromOffset(112, 14),
		Size = UDim2.new(1, -124, 1, -28),
		Text = board.title,
		TextXAlignment = Enum.TextXAlignment.Left,
		Parent = header,
	}, 4)

	board.rows = {}
	for i = 1, 10 do
		local row = Instance.new("Frame")
		row.Position = UDim2.fromOffset(16, 142 + (i - 1) * 62)
		row.Size = UDim2.new(1, -32, 0, 54)
		row.BackgroundColor3 = RANK_COLORS[i] or (i % 2 == 0 and C(72, 52, 34) or C(86, 62, 40))
		row.BorderSizePixel = 0
		row.Parent = surface
		Instance.new("UICorner").Parent = row
		uiStroke(row, 3)

		local rankBox = Instance.new("Frame")
		rankBox.Position = UDim2.fromOffset(6, 6)
		rankBox.Size = UDim2.fromOffset(42, 42)
		rankBox.BackgroundColor3 = C(25, 20, 18)
		rankBox.BackgroundTransparency = 0.4
		rankBox.BorderSizePixel = 0
		rankBox.Parent = row
		Instance.new("UICorner").Parent = rankBox
		boardText(rankBox, { Size = UDim2.fromScale(1, 1), Text = tostring(i), Parent = rankBox }, 2.5)

		board.rows[i] = {
			name = boardText(row, {
				Position = UDim2.fromOffset(58, 9),
				Size = UDim2.new(0.62, -58, 1, -18),
				TextXAlignment = Enum.TextXAlignment.Left,
				Text = "—",
				Parent = row,
			}, 3),
			value = boardText(row, {
				Position = UDim2.new(0.62, 0, 0, 9),
				Size = UDim2.new(0.38, -12, 1, -18),
				TextXAlignment = Enum.TextXAlignment.Right,
				TextColor3 = i <= 3 and WHITE or board.color:Lerp(WHITE, 0.35),
				Text = "",
				Parent = row,
			}, 3),
		}
	end

	local ok, store = pcall(function()
		return DataStoreService:GetOrderedDataStore("DestroySimTop_" .. board.id)
	end)
	board.store = ok and store or nil
	board.written = {}

	model.WorldPivot = CFrame.new()
	model:PivotTo(groundCFrame)
	model.Parent = worldsFolder
	board.avatarCFrame = groundCFrame * CFrame.new(0, 3, -8)
end

-- Доски стоят за спавном полукругом и смотрят на него
for index, board in BOARDS do
	local ground = V(-45 + (index - 1) * 30, 0, index == 1 and -158 or index == 4 and -158 or -163)
	buildBoard(board, CFrame.lookAt(ground, V(0, 0, -128)))
end

-- 3D-аватар лидера на золотом пьедестале
local function showLeader(board, userId)
	if board.avatarUser == userId then
		return
	end
	board.avatarUser = userId
	if board.avatar then
		board.avatar:Destroy()
		board.avatar = nil
	end
	if not userId then
		return
	end
	task.spawn(function()
		local ok, avatar = pcall(function()
			return Players:CreateHumanoidModelFromUserId(userId)
		end)
		if not ok or not avatar or board.avatarUser ~= userId then
			return
		end
		for _, part in avatar:GetDescendants() do
			if part:IsA("BasePart") then
				part.Anchored = true
			end
		end
		local humanoid = avatar:FindFirstChildOfClass("Humanoid")
		if humanoid then
			humanoid.DisplayDistanceType = Enum.HumanoidDisplayDistanceType.None
		end
		avatar.Name = "Leader_" .. board.id
		avatar:PivotTo(board.avatarCFrame)
		local box, size = avatar:GetBoundingBox()
		avatar:PivotTo(avatar:GetPivot() + V(0, board.avatarCFrame.Position.Y - (box.Position.Y - size.Y / 2), 0))
		avatar.Parent = worldsFolder
		board.avatar = avatar
	end)
end

local nameCache = {}
local function nameOf(userId)
	if nameCache[userId] then
		return nameCache[userId]
	end
	local online = Players:GetPlayerByUserId(userId)
	if online then
		return online.DisplayName
	end
	local ok, name = pcall(function()
		return Players:GetNameFromUserIdAsync(userId)
	end)
	nameCache[userId] = ok and name or ("Игрок " .. userId)
	return nameCache[userId]
end

local function refreshBoards()
	for _, board in BOARDS do
		local entries = nil
		if board.store then
			-- записываем текущих игроков в общую таблицу
			for _, player in Players:GetPlayers() do
				local profile = profiles[player]
				if profile and profile.loaded then
					local value = math.floor(board.value(player, profile))
					if board.written[player.UserId] ~= value then
						local ok = pcall(function()
							board.store:SetAsync(tostring(player.UserId), value)
						end)
						if ok then
							board.written[player.UserId] = value
						end
					end
				end
			end
			local ok, pages = pcall(function()
				return board.store:GetSortedAsync(false, 10)
			end)
			if ok then
				entries = {}
				for _, item in pages:GetCurrentPage() do
					table.insert(entries, { userId = tonumber(item.key), value = item.value })
				end
			end
		end
		-- если сохранения выключены (например, в Studio), показываем игроков сервера
		if not entries then
			entries = {}
			for _, player in Players:GetPlayers() do
				local profile = profiles[player]
				if profile then
					table.insert(entries, { userId = player.UserId, value = math.floor(board.value(player, profile)) })
				end
			end
			table.sort(entries, function(a, b)
				return a.value > b.value
			end)
		end
		for i, row in board.rows do
			local entry = entries[i]
			row.name.Text = entry and nameOf(entry.userId) or "—"
			row.value.Text = entry and board.format(entry.value) or ""
		end
		local leader = entries[1]
		showLeader(board, leader and leader.userId and leader.userId > 0 and leader.userId or nil)
	end
end

-- Время в игре
task.spawn(function()
	while true do
		task.wait(10)
		for _, player in Players:GetPlayers() do
			local profile = profiles[player]
			if profile then
				profile.playtime += 10
			end
		end
	end
end)

task.spawn(function()
	task.wait(5)
	while true do
		refreshBoards()
		task.wait(30)
	end
end)

print("[DestroySim] Сервер запущен! Ломай блоки ")
