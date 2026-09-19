local swing = require("EZSwing")

HoldingShield = false

local wasBlock = false
local isBlock = false
local wasBlockAngle = 0
local unblocking = -1

local isDrawing = false
local wasDrawing = false

local lastFrame = world:getTime()

UNBLOCK_TIME = 15

ANIMPATH.staff_bkR:setBlendTime(0,4)
ANIMPATH.staff_bkL:setBlendTime(0,4)

ANIMPATH.staff_bowR:setBlendTime(4)

local shieldParts = {
	MODELPATH.Shields.Shield1.cube,
	MODELPATH.Shields.Shield2.cube,
	MODELPATH.Shields.Shield3.cube,
	MODELPATH.Shields.Shield4.cube
}

-- batter up
local chained = {
	ANIMPATH.staff_atkR1,
	ANIMPATH.staff_atkR2
}

-- returns true if the item is replaced by the staff
function staffItem(item)
	local useAction = item:getUseAction()
	
	if (not StaffToggle.state) then
		return item:getID():find("sword") or item:getID():find("axe") or item:getID():find("shovel") or useAction == "BOW" or useAction == "BLOCK"
	else
		return false
	end
end

swing:addChainedSwings(chained,"right",staffItem,"attack",false,25)

function events.tick()
	if player:isLoaded() then
		-- walk trail
		if TailMultiAction.state ~= 1 then
			
			if player:getVelocity():lengthSquared() > 0.01 then
				particles["splash"]:pos(player:getPos()+vec(0,0.2,0)+PirOdd.RandomVec3(0.1)):velocity(-player:getVelocity()*0.5+vec(0,0.2,0)+PirOdd.RandomVec3(0.1)):spawn()
			end
		end
		
		if StaffToggle.state then return end
		
		local staffHand = "NONE"
		
		-- duplicate staff removal
		local mainStaff = staffItem(player:getHeldItem())
		local offStaff = staffItem(player:getHeldItem(true))
		if mainStaff and offStaff then
			-- hide offhand
			if player:isLeftHanded() then
				MODELPATH.root.UpperBody.RightArm.RightItemPivot:setScale(0)
				MODELPATH.root.UpperBody.LeftArm.LeftItemPivot:setScale(1)
				staffHand = "LEFT"
			else
				MODELPATH.root.UpperBody.RightArm.RightItemPivot:setScale(1)
				MODELPATH.root.UpperBody.LeftArm.LeftItemPivot:setScale(0)
				staffHand = "RIGHT"
			end
		else
			MODELPATH.root.UpperBody.RightArm.RightItemPivot:setScale(1)
			MODELPATH.root.UpperBody.LeftArm.LeftItemPivot:setScale(1)
			if player:isLeftHanded() then
				staffHand = mainStaff and "LEFT" or "RIGHT"
			else
				staffHand = mainStaff and "RIGHT" or "LEFT"
			end
		end
		
		-- slicing
		if MODELPATH.Slice:getVisible() then
			local matrix = MODELPATH.Slice.SliceEmitter:partToWorldMatrix()
		
			local pos = matrix:apply(PirOdd.RandomVec3(1))
			local vel = matrix:applyDir(0,0,-8)+vec(0,0.2,0)
			
			for i=1, 8 do
				particles["falling_water"]:pos(pos):velocity(vel):spawn()
			end
		end
		
		-- shields
		wasBlock = isBlock
		
		HoldingShield = player:getHeldItem():getUseAction() == "BLOCK" or player:getHeldItem(true):getUseAction() == "BLOCK"
		
		MODELPATH.Shields:setVisible(HoldingShield)
		
		if HoldingShield then
			isBlock = player:getActiveItem():getUseAction() == "BLOCK"
			
			-- tbh i don't remember what this crap does
			if isBlock and not wasBlock then
				wasBlockTime = world:getTime()
				wasBlockAngle = (world:getTime()*10)%90-45
				unblocking = -1
				
				if staffHand == "LEFT" then
					ANIMPATH.staff_bkL:play()
				else
					ANIMPATH.staff_bkR:play()
				end
				
			elseif wasBlock and not isBlock then
				wasBlockTime = world:getTime()
				wasBlockAngle = (world:getTime()*10)%90-45
				unblocking = 0
				
				ANIMPATH.staff_bkL:stop()
				ANIMPATH.staff_bkR:stop()
			elseif unblocking ~= -1 then
				unblocking = unblocking+1
				
				MODELPATH.ExtraShield:setVisible(false)
				
				if unblocking >= UNBLOCK_TIME then
					unblocking = -1
				end
			end
			
			-- blocking water sounds
			if player:isBlocking() and world:getTime()%30 == 0 then
				sounds["block.water.ambient"]:pos(player:getPos()):volume(2):pitch(1.5):play()
			end
		
			-- animate shields
			MODELPATH.Shields:setUVPixels(math.floor(world:getTime()/2)%8*9)
			
			for i,part in ipairs(shieldParts) do
				-- chance to spawn particle
				if math.random() < 0.33 then
					local pos = part:partToWorldMatrix():apply(vec(0,-4,0)+PirOdd.RandomVec3(1))
					
					particles["falling_water"]:pos(pos):spawn()
				end
			end
		elseif wasBlock then
			wasBlock = false
			ANIMPATH.staff_bkL:stop()
			ANIMPATH.staff_bkR:stop()
			MODELPATH.ExtraShield:setVisible(false)
		end
		
		-- bow
		HoldingBow = player:getHeldItem():getUseAction() == "BOW" or player:getHeldItem(true):getUseAction() == "BOW"
		-- animate charge ball
		MODELPATH.Arrow.Wave2:setUVPixels(math.floor(world:getTime())%11)
		
		if HoldingBow then
			isDrawing = player:getActiveItem():getUseAction() == "BOW"
			
			if isDrawing and not wasDrawing then
				if staffHand == "LEFT" then
					ANIMPATH.staff_bowL:play()
				else
					ANIMPATH.staff_bowR:play()
				end
			
				wasDrawing = true
			elseif not isDrawing and wasDrawing then
				wasDrawing = false
				
				ANIMPATH.staff_bowL:stop()
				ANIMPATH.staff_bowR:stop()
				MODELPATH.Charge:setVisible(false)
			end
			
			-- animate charge ball
			MODELPATH.Charge.Wave:setUVPixels(math.floor(world:getTime())%11)
		elseif wasDrawing then
			wasDrawing = false
			
			ANIMPATH.staff_bowL:stop()
			ANIMPATH.staff_bowR:stop()
			MODELPATH.Charge:setVisible(false)
		end
	end
end

function events.render(delta, context)
	if context == "PAPERDOLL" then return end
	
	local currentTime = world:getTime(delta)
	local deltaTime = lastFrame-currentTime
	lastFrame = currentTime

	MODELPATH.ItemStaff.GlowyStaff.orb:setRot(world:getTime(delta)%360,world:getTime(delta)%360,world:getTime(delta)%360)
	MODELPATH.ItemStaff.GlowyStaff.ShieldTip:setRot(0,world:getTime(delta)*3%360,0)

	if isBlock then
		MODELPATH.Shields:setRot(0,math.lerp(((currentTime-wasBlockTime)*10)+wasBlockAngle,0,math.min((ANIMPATH.staff_bkL:getTime()+ANIMPATH.staff_bkR:getTime())*4,1)),0)
	elseif unblocking ~= -1 then
		MODELPATH.Shields:setRot(0,math.lerp(0,((currentTime-wasBlockTime)*10)+wasBlockAngle,(unblocking+delta)/UNBLOCK_TIME),0)
	else
		wasBlock = false
		MODELPATH.Shields:setRot(0,(currentTime*10)%90-45,0)
	end
end

function ShieldCrash()
	if player:isLoaded() then
		MODELPATH.ExtraShield:setVisible(true)
	
		sounds["entity.zombie.attack_iron_door"]:pos(player:getPos()):volume(0.05):pitch(1.5):subtitle("Shields Crash"):play()
		sounds["minecraft:entity.generic.splash"]:pos(player:getPos()):volume(0.4):pitch(1.8):subtitle("Shields Crash"):play()
		sounds["block.water.ambient"]:pos(player:getPos()):volume(2):pitch(1.5):subtitle("Shields Crash"):play()
		
		-- body rot to dir
		local bodyRot = -math.rad(player:getBodyYaw())
		local lookDir = vec(math.sin(bodyRot),0,math.cos(bodyRot))
		
		for i = 1,8 do
			particles["splash"]:pos(player:getPos()+vec(0,1.2,0)+lookDir+PirOdd.RandomVec3(0.2)):velocity(lookDir*0.1+vec(0,0.2,0)+PirOdd.RandomVec3(0.1)):scale(2):spawn()
		end
	end
end

function SlashSound()
	if player:isLoaded() then
		sounds["entity.fish.swim"]:pos(player:getPos()):volume(0.8):pitch(1.5):subtitle("Staff Swings"):play()
	end
end