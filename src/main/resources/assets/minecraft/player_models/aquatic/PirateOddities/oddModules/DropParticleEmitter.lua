local BASEPART = models:newPart("particleBasePart", "WORLD")

local MIN_BOUNCE = 0.2
local IMPACT_FRICTION = 0.1

local particles = 1

local DropParticleEmitter = {}
DropParticleEmitter.registered = {}

local function newParticle(part, pos, vel, radius)
	local hnd = {
		part = part:copy("particle_" .. particles):setParentType("WORLD"):moveTo(BASEPART):setScale(0):setVisible(true),
		pos = pos,
		oldPos = pos,
		vel = vel,
		radius = radius,
		
		lastColision = vec(1,1,1),
		
		life = 0
	}
	
	particles = particles + 1
	
	return hnd
end

local DropEmitterBase = {
	tick = function(self)	
			for i,particle in pairs(self.particles) do
				
				if particle.life > self.liftetime then
					particle.part:remove()
					self.particles[i] = nil
				else
					--particle.vel = particle.vel* (1 - self.airFriction) + (self.force/20)
					local frictionForce = particle.vel:normalized() * (particle.vel:lengthSquared() * -self.airFriction)
					particle.vel = particle.vel + frictionForce + (self.force/20)
				
					local endPos = particle.pos + particle.vel
					local dir = particle.vel:normalized()
					local deltaMag = particle.vel:length()
					
					local rayEnd = particle.pos + dir * (deltaMag + particle.radius)
					
					local block, hitpos, side = raycast:block(particle.pos, rayEnd)
					if hitpos ~= rayEnd then
						local bounceMag = (hitpos-particle.pos):length()
						
						endPos = particle.pos + dir * (bounceMag-particle.radius)
						
						local hitStrength = 0
						
						if side == "north" or side == "south" then
							hitStrength = math.abs(vec(0,0,1):dot(particle.vel))
						
							local bounce = particle.vel.z * -self.bounceEnergy
							particle.vel.z = math.max(math.abs(bounce),0) * (side == "south" and 1 or -1)
							
							particle.lastColision.z = 0
						elseif side == "east" or side == "west" then
							hitStrength = math.abs(vec(1,0,0):dot(particle.vel))
						
							local bounce = particle.vel.x * -self.bounceEnergy
							particle.vel.x = math.max(math.abs(bounce),0) * (side == "east" and 1 or -1)
							
							particle.lastColision.x = 0
						elseif side == "up" or side == "down" then
							hitStrength = math.abs(vec(0,1,0):dot(particle.vel))
						
							local bounce = particle.vel.y * -self.bounceEnergy
							particle.vel.y = math.max(math.abs(bounce),0) * (side == "up" and 1 or -1)
							
							particle.lastColision.y = 0
						end
						
						if hitStrength > 0.08 then
							sounds["minecraft:block.honey_block.place"]:volume((hitStrength-0.05)*0.1):pos(hitpos):play()
						end
					end
				
					particle.oldPos = particle.pos
					particle.pos = endPos
					
					particle.life = particle.life + 1
					
					if particle.life < self.scaleInTime then
						particle.part:setScale(1*(particle.life/self.scaleInTime))
					elseif particle.life > self.liftetime-self.scaleOutTime then
						particle.part:setScale(1*((self.liftetime-particle.life)/self.scaleOutTime))
					else
						particle.part:setScale(1)
					end
				end
			end
		
			self.progress = self.progress + self.rate/20
			
			while self.progress > 1 do
				local particle = newParticle(self.particlePart, self.launchPos + PirOdd.RandomVec3(self.launchPosVariance), self.launchVel/20 + PirOdd.RandomVec3(self.launchVelVariance/20), 0.2)
				self.progress = self.progress -1 + PirOdd.Random(self.timeVariance)
				table.insert(self.particles, particle)
			end
		end,
	render = function(self, delta)
			for i,particle in pairs(self.particles) do
				particle.part:setPos(math.lerp(particle.oldPos, particle.pos, delta)*16)
			end
		end,
	forceSpawn = function(self)
			local particle = newParticle(self.particlePart, self.launchPos + PirOdd.RandomVec3(self.launchPosVariance), self.launchVel/20 + PirOdd.RandomVec3(self.launchVelVariance/20), 0.2)
			table.insert(self.particles, particle)
			return particle
		end,
	__type = "DropEmitterBase"
}

DropEmitterBase.__index = DropEmitterBase

function DropParticleEmitter.new(particlePart, pos, vel)
	local hnd = setmetatable({
		particlePart = particlePart,
	
		launchPos = pos,
		launchPosVariance = 0.1,
		launchVel = vel or vec(0,0,0),
		launchVelVariance = 0.5,
		rate = 40,
		timeVariance = 0,
		airFriction = 0.01,
		bounceEnergy = 0.5,
		force = vec(0,-1.5,0),
		liftetime = 60,
		
		scaleInTime = 10,
		scaleOutTime = 20,
		
		progress = 0,
		
		particles = {},
		
		enabled = true
		
	}, DropEmitterBase)
	
	table.insert(DropParticleEmitter.registered, hnd)
	return hnd
end

return DropParticleEmitter