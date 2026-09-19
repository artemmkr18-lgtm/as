require("script")

local CROUCH_ANGLE = 30
local WIND_DIST = 10
local SQRT2 = 1/math.sqrt(2)

local windflow = 0

local frontHairBounce = PirOdd.BounceValue.new(0.05, 0.3, vec(-5,-30,-30), vec(60,30,30), 2, 0.2)
local longHairBounce = PirOdd.BounceValue.new(0.3, 0.2, vec(-120,-10,-120), vec(120,10,120), 2, 0.1)
local longHairLowerBounce = PirOdd.BounceValue.new(0.4, 0.2, vec(-30,-30,-30), vec(30,30,30), 2, 0.1)

local cloakBounce = PirOdd.BounceValue.new(0.3, 0.2, vec(-30,-5,-5), vec(0,5,5), 2, 0.1)

function events.tick()
	if player:isLoaded() then
		if world:getTime()%10 == 0 then
			checkWindflow()
		end
		-- hair
		local localVel = vec(PirOdd.localVel.z,0,-PirOdd.localVel.x)*10*(PirOdd.sinWave(world:getTime(),18,0.2,1))
		
		local longHairVel = vec(bodySmoothRot.instantHeadVel.x,0,bodySmoothRot.instantHeadVel.y*0.25-PirOdd.bodyRotDelta*0.05)*0.8
		local frontVel = vec(bodySmoothRot.instantHeadVel.x,0,-bodySmoothRot.instantHeadVel.y+PirOdd.bodyRotDelta*0.3)*0.2
		
		local headPitchTarget = -(bodySmoothRot.instantHeadRot.x+bodySmoothRot.instantBodyRot.x)*0.9
		local headTiltTarget = bodySmoothRot.instantHeadRot.z+bodySmoothRot.instantBodyRot.z
		
		local windForce = vectors.rotateAroundAxis(player:getBodyYaw()-bodySmoothRot.instantHeadRot.y + (world:getTime()*0.01)%360,vec(PirOdd.sinWave(world:getTime(),35,0.4,1)+PirOdd.sinWave(world:getTime(),113,1.5,1),0,PirOdd.sinWave(world:getTime(),42,0.5,1)+PirOdd.sinWave(world:getTime(),90,1.2,1)),vec(0,1,0)) * windflow
		
		-- glide check
		if player:isGliding() then
			-- streighten force
			longHairVel = longHairVel + vec(-longHairBounce.pos.x*PirOdd.localVel.y*0.05*(PirOdd.sinWave(world:getTime(),18,0.2,1)),0,0)
		else
			-- flip up force
			longHairVel = longHairVel + vec(PirOdd.localVel.y*8,0,0) + windForce
		end
		
		frontHairBounce:updateTick(localVel+frontVel+windForce, vec(headPitchTarget*0.6,0,-headTiltTarget))
		longHairBounce:updateTick(localVel*0.75+longHairVel, vec(headPitchTarget + (player:isCrouching() and -CROUCH_ANGLE or 0),0,headTiltTarget))
		
		if bodySmoothRot.instantHeadRot.x > 0 then
			longHairBounce.max.x = -bodySmoothRot.instantHeadRot.x*1.5
		else
			longHairBounce.max.x = 0
		end
		
		local lowerHairGravity = -bodySmoothRot.instantHeadRot-longHairBounce.pos*0.8
		lowerHairGravity.y = 0
		if lowerHairGravity.x < 0 then
			lowerHairGravity.x = 0
		end
		
		longHairLowerBounce:updateTick(localVel*0.5-frontHairBounce.velocity*0.5+longHairVel*0.5, vec((player:isCrouching() and CROUCH_ANGLE or 0),0,0)+lowerHairGravity)
	end	
end

function checkWindflow()
	local windPoints = 0
	
	local testOrigin = player:getPos()+vec(0,1.1,0)
	
	local directions = {
		vec(1,0,0),
		vec(SQRT2,0,SQRT2),
		vec(0,0,1),
		vec(-SQRT2,0,SQRT2),
		vec(-1,0,0),
		vec(-SQRT2,0,-SQRT2),
		vec(0,0,-1),
		vec(SQRT2,0,-SQRT2),
		vec(0,1,0)
	}
	
	for i,v in ipairs(directions) do
		local block, hitpos = raycast:block(testOrigin, testOrigin+v*WIND_DIST)
		
		local distSq = (hitpos-testOrigin):lengthSquared()
		
		-- gain points based on distance
		windPoints = windPoints + distSq/(WIND_DIST*WIND_DIST)
	end
	
	-- turn to percentage
	windflow = math.clamp(windPoints/#directions,0,1)
end

function events.render(delta)
	if context == "PAPERDOLL" then return end
	
	MODELPATH.root.UpperBody.Head.Hair.FrontHair.CenterBang:setOffsetRot(frontHairBounce:updateRender(delta))
	MODELPATH.root.UpperBody.Head.Hair.FrontHair.LeftBang:setOffsetRot(frontHairBounce:updateRender(delta))
	MODELPATH.root.UpperBody.Head.Hair.FrontHair.RightBang:setOffsetRot(frontHairBounce:updateRender(delta))
	
	MODELPATH.root.UpperBody.Head.Hair.LongHair:setOffsetRot(longHairBounce:updateRender(delta))
	MODELPATH.root.UpperBody.Head.Hair.LongHair.LongHair2:setOffsetRot(longHairLowerBounce:updateRender(delta))
	--MODELPATH.root.UpperBody.Head.Hair.LongHair.LongHair2.LongLeftHair:setOffsetRot(longHairLowerBounce:updateRender(delta))
	
	MODELPATH.root.UpperBody.Head.Hair.LongHair:setOffsetRot(longHairBounce:updateRender(delta))
	--MODELPATH.root.UpperBody.Head.Hair.LongHair.LongHair2.LongRightHair:setOffsetRot(longHairLowerBounce:updateRender(delta))
end