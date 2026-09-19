-- HOW TO USE:
-- line up your animations with the markers in the provided bbmodel
-- the walkguide in the bbmodel moves at the speed that blocks would be moving under the player
-- this script will then dynamically adjust the speed of the animations based on the player's current speed

-- things to note:
-- climb/crawl are basically vertical movement - minecraft roates the playermodel 90d to face the ground
-- crouch puts the bottom of the model 2px into the ground, so the walkguide is raised to show the actual ground level
-- if you turn on hitboxes you can see the notches of the walkguide in-game while moving
-- you may want to delete the walkguide after you are done to save space

-- path to the bbmodel with the animations to adjust
-- expected animations (all are optional):
-- walk
-- sprint
-- crouchwalk
-- climb
-- crawl
ANIMPATH = animations.aquatic

-- keep track of time in air to more accurately detect falls
local airTime = 0

-- keep track of stride state for air animations
local strideStarted = false

-- keep track of ground state to detect jumps or landing
local wasGround = false

-- feature toggles

-- slows down the animations when in the air, giving a sort of leaping animation without manually animating anything
local airSlowdown = true

function events.tick()
	if player:isLoaded() then
		local vel = player:getVelocity()
		
		-- slows down the animations when in the air, giving a sort of leaping animation without manually animating anything (you can delete this section if you don't need it)
		if airSlowdown then
			local sitting = player:getVehicle() ~= nil or pose == "SITTING"
			
			-- check if the player is on the ground
			local ground = player:isOnGround()
			if not ground then
				-- in-air
				airTime = airTime+1
			end
			
			-- check grounded state change
			if ground ~= wasGround then
				wasGround = ground
				
				-- check if in air, or just landed
				if not ground then
					-- reset stride and airtime states
					strideStarted = false
					airTime = 0
				elseif not sitting and airTime > 3 and player:getPose() ~= "SLEEPING" then
					-- a landing occured, can run some code here like playing an animation
					if ANIMPATH.land then
						ANIMPATH.land:stop():play()
					end
				end
			end
			
			-- check if both in air and a stride has started
			if not ground and strideStarted then
				-- drastically slow down movement animations
				vel = vel*0.2
			end
		end
		
		-- check ice
		local block = world.getBlockState(player:getPos()-vec(0,0.2,0))
		if block:getFriction() > 0.9 then
			vel = vel*1.75
		end
		
		-- convert velocity into directional components
		-- vel calculations stolen from squassets
		local forwardVel = vel:dot(vectors.angleToDir(0,player:getBodyYaw()))
		local horizontalVel = vel.xz:length()*20 -- convert to m/s
		
		-- check moving backwards - if moving backwards reverse direction
		-- remove this if you have seperate backwards animations
		local dirMult = forwardVel > 0 and 1 or -1
		
		-- divide animation speed by the velocity they represent to sync up
		local walkspeed = horizontalVel/4.317* dirMult
		local sprintspeed = horizontalVel/5.612 * dirMult
		local crouchspeed = horizontalVel/1.3 * dirMult
		local climbspeed = player:getVelocity().y*20/4.317
		
		-- apply speeds
		-- these if statements can be removed for a little more efficency
		ANIMPATH.walk:setSpeed(walkspeed)
		ANIMPATH.walk_noL:setSpeed(walkspeed)
	
		--ANIMPATH.sprint:setSpeed(sprintspeed)
		
		-- crawl and crouch are the same speed
		ANIMPATH.crouchwalk:setSpeed(crouchspeed)
		ANIMPATH.crouchwalk_noL:setSpeed(crouchspeed)
	
		ANIMPATH.crawl:setSpeed(crouchspeed)
		ANIMPATH.crawl_noL:setSpeed(crouchspeed)
	end
end

function strideStart()
	strideStarted = true
end