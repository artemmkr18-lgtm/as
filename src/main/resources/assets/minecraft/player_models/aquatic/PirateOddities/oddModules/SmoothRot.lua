-- SmoothRot object
-- adds a delayed smoothing to player head movement, also propagating some of the rotation to the body and arms
-- comes with a breathing animation and reaction to velocity

local SmoothRot = {}
SmoothRot.registered = {}

local smoothRotBase = {
	enable = function(self)
			self.enabled = true
			
			self.bounce:reset()
		end,
	disable = function(self)
			self.enabled = false
			-- reset
			self.currentRot = vec(0,0,0)
			self.headPath:setOffsetRot(0,0,0)
			self.bodyRoot:setOffsetRot(0,0,0)
			for k,part in pairs(self.armPaths) do
				part:setOffsetRot(0,0,0)
			end
		end,
	setEnabled = function(self, state)
			if state then
				self.lastUpd = world:getTime()
				self:enable()
			else
				self:disable()
			end
		end,
	
	tick = function(self)
			if self.enabled then
				local targetRot = self.forceLook or PirOdd.HeadOriginRot()
				self.bounce:setTarget(targetRot*self.bodyFactor):updateTick()
				self.bounceHead:setTarget(targetRot*(1-self.bodyFactor)):updateTick()
			end
		end,
	render = function(self, delta)
			if self.enabled then
				local currTime = world:getTime(delta)
				local updDelta = currTime-self.lastUpd
				if updDelta == 0 then
					updDelta = 1
				end
			
				self.currentRot = self.bounce:updateRender(delta)
				
				self.currentRotHead = self.bounceHead:updateRender(delta)
				
				local velMod = vec(math.min(PirOdd.localVel.z+PirOdd.localVel.y*0.5,1),0,math.min(PirOdd.localVel.x,0.5))*self.velIntensity
				local breatheFactor = vec(PirOdd.sinWave(world:getTime(delta),200,self.breatheIntensity,self.breatheIntensity),0,0)

				-- rotation of head
				local headRot = (self.currentRotHead+vec(0,0,self.currentRotHead.y*self.tiltFactor*-0.5))-velMod
				headRot = headRot+breatheFactor

				-- apply and apply pos again
				self.headPath:setOffsetRot(headRot)
				
				if self.crouchMod then
					self.bodyRoot:setPos(player:isCrouching() and self.crouchBody or vec(0,0,0))
					self.headPath:setPos(player:isCrouching() and self.crouchHead or vec(0,0,0))
				end
				
				local bodyRot = ((self.currentRot+vec(0,0,self.currentRot.y*self.tiltFactor)))-breatheFactor+velMod
				
				self.bodyRoot:setOffsetRot(bodyRot)
				
				for k,part in pairs(self.armPaths) do
					part:setOffsetRot(-bodyRot*self.armFactor)
				end
				
				self.instantBodyVel = (self.instantBodyRot-bodyRot)/updDelta
				self.instantHeadVel = (self.instantHeadRot-headRot)/updDelta
				
				self.instantHeadRot = headRot
				self.instantBodyRot = bodyRot
				
				self.lastUpd = currTime
			else
				self.headPath:setRot(vanilla_model.head:getOriginRot()):setPos(vanilla_model.head:getOriginPos())
			end
		end,
	__type = "PirOddSmoothRot"
}

smoothRotBase.__index = smoothRotBase

-- smoothly look around
---@param bodyRoot Group, root part for whole upper body, should contain body, arms, and legs
function SmoothRot.new(bodyRoot)
	local hnd = setmetatable({
		bodyRoot = bodyRoot,
		headPath = bodyRoot.Head,
		
		armPaths = {bodyRoot.LeftArm, bodyRoot.RightArm},
		
		bodyFactor = 0.3,
		armFactor = 0.5,
		tiltFactor = 0.25,
		intensity = 1,
		
		breatheIntensity = 3,
		velIntensity = 20,
		
		currentRot = vec(0,0,0),
		instantHeadRot = vec(0,0,0),
		instantBodyRot = vec(0,0,0),
		
		lastUpd = world:getTime(),
		
		instantHeadVel = vec(0,0,0),
		instantBodyVel = vec(0,0,0),
		
		crouchBody = vec(0,0,0),
		crouchHead = vec(0,0,0),
		
		crouchMod = true,
		
		bounce = PirOdd.BounceValue.new(),
		bounceHead = PirOdd.BounceValue.new(),
		
		forceLook = false,
		
		enabled = true
	},smoothRotBase)
	
	-- un-parent
	hnd.headPath:setParentType("MODEL")
	
	hnd.bounce.stiffness = 0.25
	hnd.bounce.drag = 0.4
	hnd.bounce.mass = 2
	
	hnd.bounceHead.stiffness = 0.2
	hnd.bounceHead.drag = 0.2
	hnd.bounceHead.mass = 1
	
	table.insert(SmoothRot.registered, hnd)
	return hnd
end

return SmoothRot