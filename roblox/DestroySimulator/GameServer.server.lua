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
local TweenService = game:GetService("TweenService")
local HttpService = game:GetService("HttpService")
local Debris = game:GetService("Debris")

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
	MaxEquippedPets = 3, -- сколько питомцев можно надеть
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
	{ id = "efficiency", name = "⚡ Эффективность", desc = "+20% урона за уровень", baseCost = 300, growth = 3 },
	{ id = "fortune", name = "🍀 Удача", desc = "+25% монет за уровень", baseCost = 500, growth = 3 },
	{ id = "sharpness", name = "🎯 Меткость", desc = "+3% шанс крита за уровень", baseCost = 400, growth = 3 },
	{ id = "blast", name = "💥 Взрыв", desc = "+8% шанс взрыва, задевает блоки рядом", baseCost = 2000, growth = 4 },
	{ id = "auto", name = "🤖 Автокопка", desc = "Кирка сама бьёт ближайший блок", baseCost = 1500, growth = 4 },
}
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

local PETS = {
	["Курица"] = {
		rarity = "Обычный",
		bonus = 0.1,
		parts = {
			{ V(1.2, 1.0, 1.4), V(0, 0, 0), C(245, 245, 245) },
			{ V(0.8, 1.0, 0.6), V(0, 0.75, -0.75), C(245, 245, 245) },
			{ V(0.8, 0.3, 0.4), V(0, 0.75, -1.2), C(230, 150, 40) },
			{ V(0.4, 0.35, 0.2), V(0, 0.45, -1.15), C(200, 40, 40) },
			{ V(0.2, 0.2, 0.05), V(0.25, 1.0, -1.06), BLACK },
			{ V(0.2, 0.2, 0.05), V(-0.25, 1.0, -1.06), BLACK },
			{ V(0.15, 0.7, 1.0), V(0.68, 0.05, 0), C(225, 225, 225) },
			{ V(0.15, 0.7, 1.0), V(-0.68, 0.05, 0), C(225, 225, 225) },
			{ V(0.2, 0.6, 0.2), V(0.3, -0.8, 0), C(230, 150, 40) },
			{ V(0.2, 0.6, 0.2), V(-0.3, -0.8, 0), C(230, 150, 40) },
		},
	},
	["Свинка"] = {
		rarity = "Обычный",
		bonus = 0.15,
		parts = {
			{ V(1.4, 1.1, 2.0), V(0, 0, 0), C(240, 160, 160) },
			{ V(1.2, 1.1, 1.0), V(0, 0.25, -1.45), C(240, 160, 160) },
			{ V(0.6, 0.45, 0.15), V(0, 0.05, -2.02), C(225, 125, 140) },
			{ V(0.22, 0.22, 0.05), V(0.4, 0.45, -1.97), BLACK },
			{ V(0.22, 0.22, 0.05), V(-0.4, 0.45, -1.97), BLACK },
			{ V(0.4, 0.6, 0.4), V(0.45, -0.8, 0.6), C(230, 150, 150) },
			{ V(0.4, 0.6, 0.4), V(-0.45, -0.8, 0.6), C(230, 150, 150) },
			{ V(0.4, 0.6, 0.4), V(0.45, -0.8, -0.6), C(230, 150, 150) },
			{ V(0.4, 0.6, 0.4), V(-0.45, -0.8, -0.6), C(230, 150, 150) },
		},
	},
	["Овечка"] = {
		rarity = "Редкий",
		bonus = 0.3,
		parts = {
			{ V(1.5, 1.3, 2.0), V(0, 0, 0), C(235, 235, 235), Enum.Material.Fabric },
			{ V(0.9, 0.9, 0.9), V(0, 0.4, -1.35), C(215, 180, 160) },
			{ V(1.0, 0.35, 0.95), V(0, 0.9, -1.35), C(235, 235, 235), Enum.Material.Fabric },
			{ V(0.18, 0.18, 0.05), V(0.25, 0.45, -1.82), BLACK },
			{ V(0.18, 0.18, 0.05), V(-0.25, 0.45, -1.82), BLACK },
			{ V(0.35, 0.7, 0.35), V(0.45, -0.95, 0.65), C(215, 180, 160) },
			{ V(0.35, 0.7, 0.35), V(-0.45, -0.95, 0.65), C(215, 180, 160) },
			{ V(0.35, 0.7, 0.35), V(0.45, -0.95, -0.65), C(215, 180, 160) },
			{ V(0.35, 0.7, 0.35), V(-0.45, -0.95, -0.65), C(215, 180, 160) },
		},
	},
	["Волк"] = {
		rarity = "Эпический",
		bonus = 0.6,
		parts = {
			{ V(1.0, 1.0, 1.8), V(0, 0, 0), C(210, 210, 210) },
			{ V(1.35, 1.3, 0.9), V(0, 0.1, -0.65), C(200, 200, 200) },
			{ V(1.0, 0.9, 0.8), V(0, 0.35, -1.4), C(210, 210, 210) },
			{ V(0.5, 0.4, 0.45), V(0, 0.15, -1.95), C(180, 170, 160) },
			{ V(0.2, 0.15, 0.05), V(0, 0.3, -2.18), BLACK },
			{ V(0.25, 0.35, 0.15), V(0.3, 0.95, -1.3), C(200, 200, 200) },
			{ V(0.25, 0.35, 0.15), V(-0.3, 0.95, -1.3), C(200, 200, 200) },
			{ V(0.18, 0.15, 0.05), V(0.25, 0.5, -1.81), BLACK },
			{ V(0.18, 0.15, 0.05), V(-0.25, 0.5, -1.81), BLACK },
			{ V(1.4, 0.22, 0.3), V(0, -0.35, -0.95), C(200, 40, 40) },
			{ V(0.3, 0.3, 0.9), V(0, 0.25, 1.3), C(200, 200, 200) },
			{ V(0.3, 0.7, 0.3), V(0.3, -0.85, 0.6), C(200, 200, 200) },
			{ V(0.3, 0.7, 0.3), V(-0.3, -0.85, 0.6), C(200, 200, 200) },
			{ V(0.3, 0.7, 0.3), V(0.3, -0.85, -0.6), C(200, 200, 200) },
			{ V(0.3, 0.7, 0.3), V(-0.3, -0.85, -0.6), C(200, 200, 200) },
		},
	},
	["Аксолотль"] = {
		rarity = "Легендарный",
		bonus = 1.5,
		parts = {
			{ V(0.9, 0.6, 1.6), V(0, 0, 0), C(250, 170, 200) },
			{ V(1.1, 0.8, 0.9), V(0, 0.1, -1.15), C(250, 170, 200) },
			{ V(0.5, 0.12, 0.12), V(0.75, 0.45, -1.0), C(220, 60, 140) },
			{ V(0.5, 0.12, 0.12), V(0.75, 0.2, -1.0), C(220, 60, 140) },
			{ V(0.5, 0.12, 0.12), V(0.75, -0.05, -1.0), C(220, 60, 140) },
			{ V(0.5, 0.12, 0.12), V(-0.75, 0.45, -1.0), C(220, 60, 140) },
			{ V(0.5, 0.12, 0.12), V(-0.75, 0.2, -1.0), C(220, 60, 140) },
			{ V(0.5, 0.12, 0.12), V(-0.75, -0.05, -1.0), C(220, 60, 140) },
			{ V(0.15, 0.15, 0.05), V(0.35, 0.2, -1.61), BLACK },
			{ V(0.15, 0.15, 0.05), V(-0.35, 0.2, -1.61), BLACK },
			{ V(0.4, 0.06, 0.05), V(0, -0.05, -1.61), C(200, 90, 130) },
			{ V(0.12, 0.5, 1.0), V(0, 0.05, 1.3), C(240, 150, 190) },
			{ V(0.25, 0.3, 0.25), V(0.45, -0.4, 0.5), C(240, 150, 190) },
			{ V(0.25, 0.3, 0.25), V(-0.45, -0.4, 0.5), C(240, 150, 190) },
			{ V(0.25, 0.3, 0.25), V(0.45, -0.4, -0.5), C(240, 150, 190) },
			{ V(0.25, 0.3, 0.25), V(-0.45, -0.4, -0.5), C(240, 150, 190) },
		},
	},
	["Магмовый куб"] = {
		rarity = "Обычный",
		bonus = 0.8,
		parts = {
			{ V(1.6, 1.6, 1.6), V(0, 0, 0), C(60, 25, 20) },
			{ V(1.66, 0.22, 1.66), V(0, 0.0, 0), C(255, 110, 20), NEON },
			{ V(1.66, 0.22, 1.66), V(0, -0.5, 0), C(255, 110, 20), NEON },
			{ V(0.4, 0.2, 0.05), V(0.4, 0.45, -0.83), C(255, 200, 40), NEON },
			{ V(0.4, 0.2, 0.05), V(-0.4, 0.45, -0.83), C(255, 200, 40), NEON },
		},
	},
	["Страйдер"] = {
		rarity = "Редкий",
		bonus = 1.2,
		parts = {
			{ V(1.6, 1.4, 1.6), V(0, 0.5, 0), C(170, 50, 55) },
			{ V(0.12, 0.6, 0.12), V(-0.5, 1.5, 0), C(70, 35, 45) },
			{ V(0.12, 0.6, 0.12), V(0, 1.55, 0.3), C(70, 35, 45) },
			{ V(0.12, 0.6, 0.12), V(0.45, 1.45, -0.3), C(70, 35, 45) },
			{ V(0.12, 0.6, 0.12), V(0.2, 1.5, 0.5), C(70, 35, 45) },
			{ V(0.3, 0.15, 0.05), V(0.4, 0.75, -0.81), BLACK },
			{ V(0.3, 0.15, 0.05), V(-0.4, 0.75, -0.81), BLACK },
			{ V(0.9, 0.1, 0.05), V(0, 0.3, -0.81), C(60, 20, 25) },
			{ V(0.35, 1.2, 0.35), V(0.45, -0.8, 0), C(100, 80, 90) },
			{ V(0.35, 1.2, 0.35), V(-0.45, -0.8, 0), C(100, 80, 90) },
		},
	},
	["Ифрит"] = {
		rarity = "Эпический",
		bonus = 2,
		fly = true,
		parts = {
			{ V(1.0, 1.0, 1.0), V(0, 0, 0), C(255, 190, 50) },
			{ V(0.25, 0.15, 0.05), V(0.25, 0.05, -0.51), C(60, 30, 10) },
			{ V(0.25, 0.15, 0.05), V(-0.25, 0.05, -0.51), C(60, 30, 10) },
			{ V(0.22, 0.8, 0.22), V(0.85, -0.5, 0), C(255, 140, 30), NEON },
			{ V(0.22, 0.8, 0.22), V(-0.85, -0.5, 0), C(255, 140, 30), NEON },
			{ V(0.22, 0.8, 0.22), V(0, -0.5, 0.85), C(255, 140, 30), NEON },
			{ V(0.22, 0.8, 0.22), V(0, -0.5, -0.85), C(255, 140, 30), NEON },
			{ V(0.22, 0.8, 0.22), V(0.5, -1.3, 0.5), C(255, 140, 30), NEON },
			{ V(0.22, 0.8, 0.22), V(-0.5, -1.3, -0.5), C(255, 140, 30), NEON },
		},
	},
	["Гаст"] = {
		rarity = "Легендарный",
		bonus = 5,
		fly = true,
		parts = {
			{ V(2.4, 2.4, 2.4), V(0, 0, 0), C(240, 240, 240) },
			{ V(0.5, 0.2, 0.05), V(0.55, 0.35, -1.21), C(60, 60, 60) },
			{ V(0.5, 0.2, 0.05), V(-0.55, 0.35, -1.21), C(60, 60, 60) },
			{ V(0.15, 0.4, 0.05), V(0.7, 0.0, -1.21), C(170, 170, 170) },
			{ V(0.15, 0.4, 0.05), V(-0.7, 0.0, -1.21), C(170, 170, 170) },
			{ V(0.6, 0.35, 0.05), V(0, -0.45, -1.21), C(60, 60, 60) },
			{ V(0.3, 1.2, 0.3), V(-0.8, -1.8, -0.6), C(230, 230, 230) },
			{ V(0.3, 1.2, 0.3), V(0, -1.9, -0.7), C(230, 230, 230) },
			{ V(0.3, 1.2, 0.3), V(0.8, -1.75, -0.5), C(230, 230, 230) },
			{ V(0.3, 1.2, 0.3), V(-0.6, -1.85, 0.5), C(230, 230, 230) },
			{ V(0.3, 1.2, 0.3), V(0.3, -1.8, 0.6), C(230, 230, 230) },
			{ V(0.3, 1.2, 0.3), V(0.9, -1.9, 0.4), C(230, 230, 230) },
		},
	},
	["Эндермит"] = {
		rarity = "Обычный",
		bonus = 4,
		parts = {
			{ V(0.7, 0.55, 0.6), V(0, 0, 0), C(45, 35, 55) },
			{ V(0.6, 0.5, 0.6), V(0, -0.02, -0.6), C(45, 35, 55) },
			{ V(0.6, 0.5, 0.6), V(0, -0.02, 0.6), C(45, 35, 55) },
			{ V(0.4, 0.35, 0.4), V(0, -0.08, 1.1), C(45, 35, 55) },
			{ V(0.12, 0.12, 0.05), V(0.18, 0.08, -0.91), C(220, 120, 255), NEON },
			{ V(0.12, 0.12, 0.05), V(-0.18, 0.08, -0.91), C(220, 120, 255), NEON },
		},
	},
	["Шалкер"] = {
		rarity = "Редкий",
		bonus = 6,
		parts = {
			{ V(0.9, 0.9, 0.9), V(0, 0, 0), C(220, 230, 120) },
			{ V(1.6, 0.8, 1.6), V(0, 0.65, 0.2), C(150, 100, 160) },
			{ V(1.6, 0.55, 1.6), V(0, -0.6, 0.2), C(150, 100, 160) },
			{ V(0.15, 0.15, 0.05), V(0.2, 0.05, -0.46), BLACK },
			{ V(0.15, 0.15, 0.05), V(-0.2, 0.05, -0.46), BLACK },
		},
	},
	["Эндермен"] = {
		rarity = "Эпический",
		bonus = 10,
		parts = {
			{ V(0.9, 1.3, 0.5), V(0, 0, 0), C(20, 20, 25) },
			{ V(1.0, 1.0, 1.0), V(0, 1.15, 0), C(20, 20, 25) },
			{ V(0.35, 0.12, 0.05), V(0.25, 1.05, -0.51), C(220, 120, 255), NEON },
			{ V(0.35, 0.12, 0.05), V(-0.25, 1.05, -0.51), C(220, 120, 255), NEON },
			{ V(0.2, 2.0, 0.2), V(0.55, -0.35, 0), C(20, 20, 25) },
			{ V(0.2, 2.0, 0.2), V(-0.55, -0.35, 0), C(20, 20, 25) },
			{ V(0.22, 1.8, 0.22), V(0.22, -1.55, 0), C(20, 20, 25) },
			{ V(0.22, 1.8, 0.22), V(-0.22, -1.55, 0), C(20, 20, 25) },
		},
	},
	["Дракон Края"] = {
		rarity = "Мифический",
		bonus = 30,
		fly = true,
		parts = {
			{ V(1.2, 1.0, 2.4), V(0, 0, 0), C(25, 20, 30) },
			{ V(0.6, 0.6, 0.8), V(0, 0.3, -1.5), C(25, 20, 30) },
			{ V(0.9, 0.75, 1.0), V(0, 0.45, -2.3), C(25, 20, 30) },
			{ V(0.6, 0.4, 0.5), V(0, 0.3, -3.0), C(35, 30, 40) },
			{ V(0.2, 0.1, 0.05), V(0.3, 0.65, -2.81), C(220, 120, 255), NEON },
			{ V(0.2, 0.1, 0.05), V(-0.3, 0.65, -2.81), C(220, 120, 255), NEON },
			{ V(2.6, 0.1, 1.4), V(1.9, 0.45, -0.1), C(60, 55, 70) },
			{ V(2.6, 0.1, 1.4), V(-1.9, 0.45, -0.1), C(60, 55, 70) },
			{ V(0.4, 0.4, 1.8), V(0, 0.1, 2.1), C(25, 20, 30) },
			{ V(0.3, 0.3, 0.8), V(0, 0.05, 3.3), C(25, 20, 30) },
			{ V(0.15, 0.3, 0.3), V(0, 0.65, -0.7), C(120, 115, 130) },
			{ V(0.15, 0.3, 0.3), V(0, 0.65, 0), C(120, 115, 130) },
			{ V(0.15, 0.3, 0.3), V(0, 0.65, 0.7), C(120, 115, 130) },
		},
	},
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
	overworld = { floor = C(95, 159, 53), column = C(134, 96, 67), columnTop = C(95, 159, 53) },
	nether = { floor = NETHERRACK, floorAlt = C(84, 64, 51), column = C(100, 45, 45) },
	ender = { floor = C(219, 222, 158), column = C(205, 208, 145) },
}

local COIN_STAT = "Монеты"
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
local eggResultRemote = makeRemote("RemoteEvent", "EggOpened")
local worldRemote = makeRemote("RemoteEvent", "World")
local tradeRemote = makeRemote("RemoteEvent", "Trade")

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

local function spawnCFrame(id)
	local position = worldOrigin(id) + V(0, 4, SPAWN_Z)
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
	addLabel(top, "🌍 " .. target.name .. (target.price > 0 and ("\n💰 " .. abbreviate(target.price)) or ""), target.accent, V(0, 3, 0))

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

local function buildEggStand(folder, position, world)
	local egg = world.egg
	local stand = Instance.new("Model")
	stand.Name = "EggStand"
	local pedestal = makePart({ Name = "Pedestal", Size = V(7, 2, 7), Position = position + V(0, 1, 0), Color = C(60, 60, 70), Parent = stand })
	makePart({ Size = V(4, 2, 4), Position = position + V(0, 3, 0), Color = egg.color, Parent = stand })
	local middle = makePart({ Size = V(5, 3, 5), Position = position + V(0, 5.5, 0), Color = egg.color, Parent = stand })
	local top = makePart({ Size = V(3.5, 2, 3.5), Position = position + V(0, 8, 0), Color = egg.color, Parent = stand })
	addSpots(stand, middle, { egg.spot }, 3, { sidesOnly = true })
	addLabel(top, "🥚 " .. egg.name .. "\n💰 " .. abbreviate(egg.price), GOLD, V(0, 3.5, 0))

	local prompt = Instance.new("ProximityPrompt")
	prompt.ActionText = "Открыть"
	prompt.ObjectText = egg.name .. " (💰 " .. abbreviate(egg.price) .. ")"
	prompt.HoldDuration = 0
	prompt.MaxActivationDistance = 12
	prompt.RequiresLineOfSight = false
	prompt.Parent = pedestal
	prompt.Triggered:Connect(function(player)
		openEgg(player, world.id)
	end)
	stand.Parent = folder
end

local function buildWorld(world)
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
				Color = shade(color, 0.12),
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
	buildEggStand(folder, origin + V(26, 0, SPAWN_Z), world)

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

local function coinMultiplier(player)
	local rebirths = stat(player, REBIRTH_STAT)
	local rebirthMult = 1 + (rebirths and rebirths.Value or 0) * CONFIG.RebirthBonus
	return rebirthMult * (1 + petBonus(player)) * (1 + addonLevel(player, "fortune") * 0.25)
end

local function damageOf(player)
	local level = player:GetAttribute("Level") or 1
	local profile = profiles[player]
	local pickaxe = PICKAXES[profile and profile.pickaxe or 1]
	local efficiency = 1 + addonLevel(player, "efficiency") * 0.2
	return math.max(1, math.floor(level ^ 1.5 * pickaxe.damage * efficiency))
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
	}
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
		if findPet(profile, id) and #profile.equipped < CONFIG.MaxEquippedPets then
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
			announce("Не хватает монет!", RED, player)
			return
		end
		player:SetAttribute("Level", level)
		refreshPlayer(player)
		if bought > 1 then
			announce("⛏ Уровень кирки +" .. bought .. "!", GOLD, player)
		end
	elseif action == "rebirth" then
		local cost = rebirthCost(rebirths.Value)
		if coins.Value < cost then
			announce("Для ребёрта нужно 💰 " .. abbreviate(cost), RED, player)
			return
		end
		coins.Value = 0
		rebirths.Value += 1
		player:SetAttribute("Level", 1)
		refreshPlayer(player)
		announce("🔁 " .. player.DisplayName .. " сделал ребёрт #" .. rebirths.Value .. "!", C(190, 120, 255))
	elseif action == "pickaxe" then
		local nextTier = profile.pickaxe + 1
		local def = PICKAXES[nextTier]
		if not def then
			return
		end
		if coins.Value < def.price then
			announce("Нужно 💰 " .. abbreviate(def.price), RED, player)
			return
		end
		coins.Value -= def.price
		profile.pickaxe = nextTier
		refreshPlayer(player)
		giveTool(player)
		announce("⛏ " .. player.DisplayName .. " получил: " .. def.name .. "!", def.color)
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
			announce("Нужно 💰 " .. abbreviate(cost), RED, player)
			return
		end
		coins.Value -= cost
		profile.addons[addon.id] = level + 1
		refreshPlayer(player)
		announce(addon.name .. " → ур. " .. (level + 1), GOLD, player)
	end
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
			announce("Мир «" .. world.name .. "» стоит 💰 " .. abbreviate(world.price), RED, player)
			return
		end
		coins.Value -= world.price
		profile.worlds[worldId] = true
		refreshPlayer(player)
		announce("🌍 " .. player.DisplayName .. " открыл мир «" .. world.name .. "»!", world.accent)
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
		announce("Яйцо стоит 💰 " .. abbreviate(world.egg.price), RED, player)
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
	if #profile.equipped < CONFIG.MaxEquippedPets then
		table.insert(profile.equipped, pet.id)
	end
	refreshPets(player)
	eggResultRemote:FireClient(player, kind)

	local rarity = PETS[kind].rarity
	if rarity == "Легендарный" or rarity == "Мифический" then
		announce("🎉 " .. player.DisplayName .. " выбил питомца «" .. kind .. "» (" .. rarity .. ")!", RARITIES[rarity].color)
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
		for i = 1, math.min(CONFIG.MaxEquippedPets, #sorted) do
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
		if #profile.equipped >= CONFIG.MaxEquippedPets then
			announce("Можно надеть только " .. CONFIG.MaxEquippedPets .. " питомцев", RED, player)
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
		endTrade(trade, "❌ Трейд отменён", RED)
		return
	end
	for _, side in { { pa, trade.offers[a] }, { pb, trade.offers[b] } } do
		for _, id in side[2] do
			if not findPet(side[1], id) then
				endTrade(trade, "❌ Питомец пропал — трейд отменён", RED)
				return
			end
		end
	end
	if
		#pa.pets - #trade.offers[a] + #trade.offers[b] > CONFIG.MaxPets
		or #pb.pets - #trade.offers[b] + #trade.offers[a] > CONFIG.MaxPets
	then
		endTrade(trade, "❌ У кого-то не хватает места для питомцев", RED)
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
	endTrade(trade, "🤝 Трейд завершён!", GREEN)
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
		announce("📨 Запрос отправлен: " .. target.DisplayName, WHITE, player)
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
		endTrade(trade, "❌ Трейд отменён", RED)
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

local function freeTile(worldId)
	local half = CONFIG.WorldTiles / 2
	for _ = 1, 30 do
		local i = math.random(-half + 1, half - 2)
		local j = math.random(-half + 5, half - 2) -- первые ряды заняты спавном, порталами и яйцом
		local key = i .. "," .. j
		if not occupied[worldId][key] then
			return i, j, key
		end
	end
	return nil, nil, nil
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
			end
			if reward > topReward then
				topPlayer, topReward = player, reward
			end
		end
	end

	if topPlayer then
		popup(data.core.Position + V(0, data.core.Size.Y / 2, 0), "+" .. abbreviate(topReward) .. " 💰", GOLD, true)
		if data.def.announce then
			announce(
				"💎 " .. topPlayer.DisplayName .. " разбил «" .. data.def.name .. "» и получил 💰 " .. abbreviate(topReward) .. "!",
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
	local i, j, key = freeTile(world.id)
	if not i then
		return
	end
	local s = def.size or CONFIG.BlockSize
	local T = CONFIG.TileSize
	local position = worldOrigin(world.id) + V((i + 0.5) * T, s / 2, (j + 0.5) * T)
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
		announce("✨ В мире «" .. world.name .. "» появилась " .. def.name .. "! ✨", def.spot or def.color)
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
-- ИНФОРМАЦИЯ ДЛЯ ИНТЕРФЕЙСА
------------------------------------------------------------------
local GAME_INFO = {
	worlds = {},
	pets = {},
	rarities = RARITIES,
	pickaxes = {},
	addons = {},
	maxEquipped = CONFIG.MaxEquippedPets,
	maxPets = CONFIG.MaxPets,
	comboWindow = CONFIG.ComboWindow,
	tradeCountdown = CONFIG.TradeCountdown,
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
	})
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
		announce("🔥 ЗОЛОТАЯ ЛИХОРАДКА! Монеты x" .. CONFIG.EventCoinMultiplier .. "! 🔥", GOLD)

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

print("[DestroySim] Сервер запущен! Ломай блоки 💥")
