local PirOdd = require("PirateOddities/PirMain")
require("GSAnimBlend")
local anims = require("EZAnims")

function deepcopy(model)
    local copy = model:copy(model:getName())
    for _, child in pairs(copy:getChildren()) do
        copy:removeChild(child):addChild(deepcopy(child)):parentType()
    end
    return copy
end

MODELPATH = models.aquatic
ANIMPATH = animations.aquatic

animHandle = anims:addBBModel(ANIMPATH)

vanilla_model.PLAYER:setVisible(false)
vanilla_model.ARMOR:setVisible(false)

MODELPATH.root.UpperBody.Head.LeftFin:setPrimaryRenderType("CUTOUT_CULL")
MODELPATH.root.UpperBody.Head.RightFin:setPrimaryRenderType("CUTOUT_CULL")
MODELPATH.root.UpperBody.Body.Hip.SmallTail:setPrimaryRenderType("CUTOUT_CULL")
MODELPATH.root.SnakeBody:setPrimaryRenderType("CUTOUT_CULL")

MODELPATH.Slice:setPrimaryRenderType("CUTOUT_CULL"):setSecondaryTexture("CUSTOM", textures["magic"]):setVisible(false)
MODELPATH.ExtraShield:setPrimaryRenderType("CUTOUT_CULL"):setSecondaryTexture("CUSTOM", textures["magic"]):setVisible(false)
MODELPATH.ItemStaff.GlowyStaff:setPrimaryRenderType("CUTOUT_CULL"):setSecondaryTexture("CUSTOM", textures["magic"])
MODELPATH.Charge:setPrimaryRenderType("CUTOUT_CULL"):setSecondaryTexture("CUSTOM", textures["magic"]):setVisible(false)
MODELPATH.Arrow:setPrimaryRenderType("CUTOUT_CULL"):setSecondaryTexture("CUSTOM", textures["magic"])

deepcopy(MODELPATH.root.UpperBody.Head):setParentType("PORTRAIT"):moveTo(MODELPATH):setPos(0,-25,0)
deepcopy(MODELPATH.root.UpperBody.Head):setParentType("Skull"):moveTo(MODELPATH):setPos(0,-25,0)

-- anim setup
ANIMPATH.bowR:setPriority(2)
ANIMPATH.bowL:setPriority(2)
ANIMPATH.crossR:setPriority(2)
ANIMPATH.crossL:setPriority(2)
ANIMPATH.spyglassR:setPriority(2)
ANIMPATH.spyglassL:setPriority(2)
ANIMPATH.loadR:setPriority(2)
ANIMPATH.loadL:setPriority(2)

ANIMPATH.pose:setPriority(5)

ANIMPATH.tailwrap:setBlendTime(10)
animHandle:setBlendTimes(6,0)
animHandle:addIncluOverrider(ANIMPATH.staff_atkR1,ANIMPATH.staff_atkR2, ANIMPATH.staff_bowR, ANIMPATH.staff_bowL)
ANIMPATH.eatR:setBlendTime(4)
ANIMPATH.drinkR:setBlendTime(4)
ANIMPATH.spyglassR:setBlendTime(4)
ANIMPATH.eatL:setBlendTime(4)
ANIMPATH.drinkL:setBlendTime(4)
ANIMPATH.spyglassL:setBlendTime(4)

-- libary calls for physics
bodySmoothRot = PirOdd.SmoothRot.new(MODELPATH.root.UpperBody)

local eyes = PirOdd.Eyes.new(MODELPATH.root.UpperBody.Head.Eyes.LEye, MODELPATH.root.UpperBody.Head.Eyes.REye, ANIMPATH.blink, ANIMPATH.closeeyes)

local breasts = PirOdd.BouncyPart.new(MODELPATH.root.UpperBody.Body.UpperTorso.Breasts, vec(0,0.15,0), vec(1.8,2,1))

breasts.rotBounce.min = vec(-30,-20,-10)
breasts.rotBounce.max = vec(30,15,10)

breasts.posBounce.min = vec(0,-0.5,0)
breasts.posBounce.max = vec(0,0.5,0)

breasts.breastStuff = true

hairBun = PirOdd.BouncyPart.new(MODELPATH.root.UpperBody.Head.Hair.Bun, vec(0,0.05,0), vec(-2,1.2,1))

hairBun.rotBounce.min = vec(-30,-20,-10)
hairBun.rotBounce.max = vec(30,15,10)

hairBun.posBounce.min = vec(0,-0.25,0)
hairBun.posBounce.max = vec(0,0.25,0)

local frontHairBounce = PirOdd.BounceValue.new(0.05, 0.25, vec(-5,-30,-30), vec(30,30,30), 2, 0.2)

local spinBounce = PirOdd.BounceValue.new(0.6, 0.15, vec(0,0,0), vec(1,0,0), 1, 0.05)

local ears = PirOdd.Ears.new(MODELPATH.root.UpperBody.Head.LeftFin, MODELPATH.root.UpperBody.Head.RightFin, -30,25)
ears.flickChance = -1
ears.velIntensity = 10
ears.horizontal = true

ears.minYaw = -30
ears.maxYaw = 30
		
ears.minPitch = -15
ears.maxPitch = 15

SmallTail = PirOdd.Tail.new(MODELPATH.root.UpperBody.Body.Hip.SmallTail,nil,nil)

local skirt = PirOdd.Skirt.new(MODELPATH.root.UpperBody.Body.Hip.Skirt)
skirt.legMultiplier = 0
skirt.rotModPitch = 0.25
skirt.rotMod = 0.2

function events.render(delta, context)
	MODELPATH.root.UpperBody.Body.Hip:setOffsetRot(-bodySmoothRot.instantBodyRot)
end

function events.item_render(item, context)
	local firstPerson = context:find("^FIRST_PERSON")

	if not (StaffToggle.state) then
		local useAction = item:getUseAction()
		
		local sword = item:getID():find("sword")
		local axe = item:getID():find("axe")
		local pickaxe = item:getID():find("pickaxe")
		local shovel = item:getID():find("shovel")
		local shield = useAction == "BLOCK"
		local bow = useAction == "BOW"
		
		if sword or axe or pickaxe or shovel or shield or bow then
			MODELPATH.ItemStaff.GlowyStaff.SwordTip:setVisible(sword)
			MODELPATH.ItemStaff.GlowyStaff.AxeTip:setVisible(axe and not pickaxe)
			MODELPATH.ItemStaff.GlowyStaff.PickaxeTip:setVisible(pickaxe)
			MODELPATH.ItemStaff.GlowyStaff.ShovelTip:setVisible(shovel)
			
			MODELPATH.ItemStaff.GlowyStaff.ShieldTip:setVisible(shield or HoldingShield)
		
			if firstPerson then
				MODELPATH.ItemStaff:setScale(0.5)
			else
				MODELPATH.ItemStaff:setScale(1)
			end
		
			return MODELPATH.ItemStaff
		end
	end
end