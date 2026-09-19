PirOdd = require("PirateOddities/PirMain")

function deepcopy(model)
    local copy = model:copy(model:getName())
    for _, child in pairs(copy:getChildren()) do
        copy:removeChild(child):addChild(deepcopy(child)):parentType()
    end
    return copy
end

MODELPATH = models.yokai
ANIMPATH = animations.yokai

-- animation setup
ANIMPATH.leftSleeve:play():setSpeed(0)
ANIMPATH.rightSleeve:play():setSpeed(0)

ANIMPATH.pose:setPriority(3)

-- model setup
vanilla_model.PLAYER:setVisible(false)
vanilla_model.ARMOR:setVisible(false)

MODELPATH.ItemLantern.glow:setSecondaryTexture("CUSTOM", textures["lantern"]):setLight(15,15)

MODELPATH:setPos(0,1,0) -- raise up for platform shoes (may be used for outfits later)

deepcopy(MODELPATH.root.UpperBody.Head):setParentType("PORTRAIT"):moveTo(MODELPATH):setPos(0,-23,0)
deepcopy(MODELPATH.root.UpperBody.Head):setParentType("SKULL"):moveTo(MODELPATH):setPos(0,-23,0)

-- libary calls
local bodySmoothRot = PirOdd.SmoothRot.new(MODELPATH.root.UpperBody)
bodySmoothRot.crouchBody = vec(0,1,0.5)
bodySmoothRot.crouchHead = vec(0,-3,0)

local eyes = PirOdd.Eyes.new(MODELPATH.root.UpperBody.Head.Eyes.LEye, MODELPATH.root.UpperBody.Head.Eyes.REye, ANIMPATH.blink, ANIMPATH.closeeyes)

local breasts = PirOdd.BouncyPart.new(MODELPATH.root.UpperBody.Body.UpperTorso.Breasts, vec(0,0.1,0), vec(1.5,2,1))

breasts.rotBounce.min = vec(-20,-15,-10)
breasts.rotBounce.max = vec(20,15,10)

breasts.posBounce.min = vec(0,-0.25,0)
breasts.posBounce.max = vec(0,0.25,0)

breasts.breastStuff = true

-- want to disable breast physics? set this to false
breasts.enabled = true 

local frontHairBounce = PirOdd.BounceValue.new(0.05, 0.25, vec(-2.5,-30,-30), vec(30,30,30), 2, 0.2)
local longHairBounce = PirOdd.BounceValue.new(0.1, 0.2, vec(-120,-120,-120), vec(30,120,120), 3, 0.25)

local skirt = PirOdd.Skirt.new(MODELPATH.root.UpperBody.Body.Hip.Skirt)
skirt.angleAdd = 10

function events.tick()
	if player:isLoaded() then
		-- hair
		local localVel = vec(PirOdd.localVel.z,0,-PirOdd.localVel.x)*10
		
		local longHairVel = vec(bodySmoothRot.instantHeadVel.x-PirOdd.localVel.y*8,0,bodySmoothRot.instantHeadVel.y*0.5-PirOdd.bodyRotDelta*0.1)*0.8
		local frontVel = vec(bodySmoothRot.instantHeadVel.x,0,bodySmoothRot.instantHeadVel.y+PirOdd.bodyRotDelta*0.3)*0.2
		
		local headPitchTarget = (bodySmoothRot.instantHeadRot.x+bodySmoothRot.instantBodyRot.x)*-0.9
		local headTiltTarget = bodySmoothRot.instantHeadRot.z+bodySmoothRot.instantBodyRot.z
		
		frontHairBounce:updateTick(localVel+frontVel, vec(headPitchTarget*0.6,0,headTiltTarget))
		
		longHairBounce:updateTick(localVel+longHairVel, vec(headPitchTarget,0,headTiltTarget))
		
		-- lantern animations
		ANIMPATH.holdLanternR:setPlaying(player:getHeldItem(player:isLeftHanded()):getID():find("lantern"))
		ANIMPATH.holdLanternL:setPlaying(player:getHeldItem(not player:isLeftHanded()):getID():find("lantern"))
	end
end

function events.render(delta, context)
	-- hide long sleeves in first person
	MODELPATH.root.UpperBody.LeftArm.LeftSleeve:setVisible(context ~= "FIRST_PERSON")
	MODELPATH.root.UpperBody.RightArm.RightSleeve:setVisible(context ~= "FIRST_PERSON")

	if context == "PAPERDOLL" then return end
	
	-- apply hair bounces
	MODELPATH.root.UpperBody.Head.Hair.FrontHair:setRot(frontHairBounce:updateRender(delta,delta))
	MODELPATH.root.UpperBody.Head.Hair.LeftHair:setOffsetRot(longHairBounce:updateRender(delta,delta))
	MODELPATH.root.UpperBody.Head.Hair.RightHair:setOffsetRot(longHairBounce:updateRender(delta,delta))

	local hipOffset = -bodySmoothRot.instantBodyRot
	MODELPATH.root.UpperBody.Body.Hip:setRot(hipOffset):setPos(0,player:isCrouching() and 1 or 0,0)
	
	-- offset skirt tilt to reduce clipping
	if player:isCrouching() then
		MODELPATH.root.UpperBody.Body.Hip.Skirt:setOffsetRot(0,0,hipOffset.y)
	else
		MODELPATH.root.UpperBody.Body.Hip.Skirt:setOffsetRot(0,0,0)
	end
	
	-- arm sway
	if ANIMPATH.holdLanternR:isPlaying() then
		MODELPATH.root.UpperBody.RightArm:setOffsetRot(vanilla_model.RIGHT_ARM:getOriginRot()*0.1)
	else
		MODELPATH.root.UpperBody.RightArm:setOffsetRot(0,0,0)
	end
	
	if ANIMPATH.holdLanternL:isPlaying() then
		MODELPATH.root.UpperBody.LeftArm:setOffsetRot(vanilla_model.LEFT_ARM:getOriginRot()*0.1)
	else
		MODELPATH.root.UpperBody.LeftArm:setOffsetRot(0,0,0)
	end
	
	-- sleeve mapping
	local leftPitch = vanilla_model.LEFT_ARM:getOriginRot().x*(MODELPATH.root.UpperBody.LeftArm:overrideVanillaRot() and 0 or 1) +MODELPATH.root.UpperBody.LeftArm:getAnimRot().x +MODELPATH.root.UpperBody.LeftArm:getOffsetRot().x
	
	local leftMap = math.map(leftPitch,-90,90,1,0)
	ANIMPATH.leftSleeve:setTime(leftMap)
	
	local rightPitch = vanilla_model.RIGHT_ARM:getOriginRot().x*(MODELPATH.root.UpperBody.RightArm:overrideVanillaRot() and 0 or 1) +MODELPATH.root.UpperBody.RightArm:getAnimRot().x +MODELPATH.root.UpperBody.RightArm:getOffsetRot().x
	
	local leftMap = math.map(rightPitch,-90,90,1,0)
	ANIMPATH.rightSleeve:setTime(leftMap)
end

function events.item_render(item, context)
	if item.id:find("lantern") then
		return MODELPATH.ItemLantern:setPos(0,context:find("FIRST_PERSON") and 8 or 0,0)
	end
end