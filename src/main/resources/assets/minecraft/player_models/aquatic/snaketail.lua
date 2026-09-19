local GRAVITY = -9.81 -- blocks/s^2
local WATER_GRAVITY = -1 -- blocks/s^2
local COLLISION_MARGIN = 0.25
local JOINT_DISTANCE_MARGIN = 0.05
local TICK_DELTATIME = (1/20)^2 -- seconds per tick squared
local GROUND_CHECK_DISTANCE = 0.1
local FRICTION_STREIGHTENING_FORCE = 1
local TWOPI = math.pi*2
local WATER_FRICTION = 0.8

local prevFrame = world:getTime()
local deltaTime = 0

local PirOdd = require("PirateOddities/PirMain")

local normals = {
	["north"] = vec(0,0,-1),
	["east"] = vec(1,0,0),
	["south"] = vec(0,0,1),
	["west"] = vec(-1,0,0),
	
	["up"] = vec(0,1,0),
	["down"] = vec(0,-1,0)
}

local normalMask = {
	["north"] = vec(1,1,0),
	["east"] = vec(0,1,1),
	["south"] = vec(1,1,0),
	["west"] = vec(0,1,1),
	
	["up"] = vec(1,0,1),
	["down"] = vec(1,0,1)
}

function EularToDir(angle)
	return vec(
		math.cos(angle.y)*math.cos(angle.x),
		math.sin(angle.y)*math.cos(angle.x),
		math.sin(angle.x)
	)
end

function isInsideBlock(pos,size)
    local block = world.getBlockState(pos)
	local localPos = pos - block:getPos()
	
	local shapes = block:getCollisionShape()
	for i,box in ipairs(shapes) do
		if localPos >= box[1] and localPos <= box[2] then
			return block
		end
	end
end

function pushOutBlock(pos, depth)
	
	depth = depth or 1
	if depth > 3 then return pos end

    local block = world.getBlockState(pos)
	local localPos = pos - block:getPos()
	
	local shapes = block:getCollisionShape()
	for i,box in ipairs(shapes) do
		if localPos >= box[1] and localPos <= box[2] then		
			-- direction to edges
			local upDiff = box[2].y-localPos.y
			local downDiff = localPos.y-box[1].y
			local southDiff = box[2].z-localPos.z
			local northDiff = localPos.z-box[1].z
			local eastDiff = box[2].x-localPos.x
			local westDiff = localPos.x-box[1].x
			
			-- find closest
			local dist = upDiff
			local side = vec(0,1,0)
			
			if downDiff < dist then
				if not isInsideBlock(pos+vec(0,-1,0)*(downDiff+COLLISION_MARGIN)) then
					dist = downDiff
					side = vec(0,-1,0)
				end
			end
			
			if southDiff < dist then
				if not isInsideBlock(pos+vec(0,0,1)*(southDiff+COLLISION_MARGIN)) then
					dist = southDiff
					side = vec(0,0,1)
				end
			end
			
			if northDiff < dist then
				if not isInsideBlock(pos+vec(0,0,-1)*(northDiff+COLLISION_MARGIN)) then
					dist = northDiff
					side = vec(0,0,-1)
				end
			end
			
			if eastDiff < dist then
				if not isInsideBlock(pos+vec(1,0,0)*(eastDiff+COLLISION_MARGIN)) then
					dist = eastDiff
					side = vec(1,0,0)
				end
			end
			
			if westDiff < dist then
				if not isInsideBlock(pos+vec(-1,0,0)*(westDiff+COLLISION_MARGIN)) then
					dist = westDiff
					side = vec(-1,0,0)
				end
			end
			
			--particles["electric_spark"]:pos(pos+side*dist):color(0,1,1):lifetime(0):spawn()
			
			return pushOutBlock(pos+side*dist, depth+1)
		end
	end
	
	return pos
end

local TailNode = {
	getEndPos = function(this)
			return this.pos+this.length*this.dir
		end,
	getRenderEndPos = function(this, delta)
			return this.renderPos+this.renderLength*math.lerp(this.oldDir,this.dir,delta)
		end,
	tick = function(this)
			-- first tick fix
			if not this.pos then
				this.pos = player:getPos()+vec(0,this.index*this.length,0)
				this.oldPos = this.pos
			end
	
			-- save old
			local posCopy = this.pos
			this.oldDir = this.dir
	
			-- find previous node position
			local anchorDir
			local anchorPos
			local anchorMatrix
			if this.prev then
				anchorPos = this.prev:getEndPos()
				anchorMatrix = this.prev.mat
				anchorDir = this.prev.dir
			else
				anchorPos = this.parent.anchorPos
				anchorMatrix = this.parent.anchorMat
				anchorDir = anchorMatrix:applyDir(0,0,1):normalize()
			end
			
			-- calculate local cordinate system
			local xAxis = anchorMatrix:applyDir(1,0,0):normalize()
			local yAxis = anchorMatrix:applyDir(0,1,0):normalize()
			this.anchorDir = anchorDir
			
			local acceleration = vec(0,0,0)
			-- determine next position
			--tension force
			local tensionDir = (anchorPos-this.pos)
			acceleration = acceleration+tensionDir*this.parent.tension
			
			--drawVec(this.pos, this.vel,vec(1,0,0))
			
			local vel = this.pos-this.oldPos
			local friction = vel*this.parent.airFriction
			
			-- check block below
			local block = isInsideBlock(this.pos-vec(0,this.height+GROUND_CHECK_DISTANCE,0))
			if block then
				-- friction
				local blockFriction = block:getFriction()
				friction = vel*(1-blockFriction)
				this.inertia = 1/(blockFriction*blockFriction*blockFriction)
				
				-- fall but much less
				acceleration = acceleration+vec(0,GRAVITY,0)*0.05
				
				-- small streightening force, mostly to resist sliding
				acceleration = acceleration+this.dir*FRICTION_STREIGHTENING_FORCE*blockFriction
				
				--slither
				local slitherForce = xAxis*this.parent.currentSlither*math.sin(world:getTime()*TWOPI/this.parent.slitherPeriod-this.parent.marchedDistance/this.parent.slitherLengthPeriod)
				--drawVec(this.pos,slitherForce/32,vec(0,1,0))
				acceleration = acceleration+slitherForce
			elseif world.getBlockState(this.pos):getID() == "minecraft:water" then
				-- fall slower
				acceleration = acceleration+vec(0,WATER_GRAVITY,0)
				this.inertia = 1
				friction = vel*WATER_FRICTION
				
				--slither
				local slitherForce = xAxis*this.parent.currentSlither*math.sin(world:getTime()*TWOPI/this.parent.slitherPeriod-this.parent.marchedDistance/this.parent.slitherLengthPeriod)
				--drawVec(this.pos,slitherForce/32,vec(0,1,0))
				acceleration = acceleration+slitherForce
			else
				-- fall
				acceleration = acceleration+vec(0,GRAVITY,0)
				this.inertia = 1
				friction = vel*this.parent.airFriction
			end
			
			-- apply forces and move the node
			this.pos = this.pos*2-this.oldPos+acceleration*TICK_DELTATIME-friction
			this.oldPos = posCopy
			
			--particles["electric_spark"]:pos(this.pos):color(0,0,1):lifetime(0):spawn()
			
			--check collision
			local startPos = posCopy
			--local rayDir = this.pos-startPos
			local endPos = this.pos--startPos+rayDir:normalize()*(rayDir:length()+this.height)
			local block, hitpos, side = raycast:block(startPos, endPos, "COLLIDER")
			
			--drawVec(startPos, rayDir,vec(0,1,0))
			--particles["electric_spark"]:pos(hitpos):color(0,1,0):lifetime(0):spawn()
			
			if hitpos ~= endPos then
				--local hitDir = hitpos-startPos
				this.pos = hitpos
				--this.pos = hitpos
				--drawDir(hitpos,normals[side],vec(0,1,0))
			end
			
			-- angle constraints
			if this.prev then
				this.prev.dir = (this.pos-this.prev.pos):normalize()
				
				--drawDir(this.prev.pos, this.prev.dir,vec(1,0,0))
				--drawDir(this.prev.pos, this.prev.anchorDir,vec(1,0,1))
				
				--angle clamp
				local angle = math.deg(math.acos(this.prev.dir:dot(this.prev.anchorDir)))
				--print(this.prev.index, math.deg(angle))
				
				-- check exceeding angle
				if angle > this.parent.maxAngle then
					--local toEdge = this.parent.maxAngle/angle
					--this.prev.dir = math.lerp(this.prev.anchorDir,this.prev.dir,toEdge)
					
					-- rotate to be within angle
					local axis = this.prev.dir:crossed(this.prev.anchorDir)
					this.prev.dir = vectors.rotateAroundAxis(angle-this.parent.maxAngle, this.prev.dir, axis)
					
					--drawDir(this.prev.pos, axis,vec(0,0,0))
					--drawDir(this.prev.pos, this.prev.dir,vec(1,1,0))
					-- pull node in
					this.pos = this.prev.pos+this.prev.dir*this.length--math.lerp(this.pos,this.prev.dir*dist,0.5)
					--particles["electric_spark"]:pos(this.pos+vec(0,1,0)):color(1,0,1):lifetime(0):spawn()
				end
				
				-- create rotation matrix
				this.prev.mat = matrices.mat4():rotate(PirOdd.DirToAngles(this.prev.dir))
			end
		end,
	constrain = function(this)
			if this.prev then
				local dir = this.pos-this.prev.pos
				
				local dist = dir:length() - this.length
				dir:normalize()
				
				--dist = dist*1.1
				
				-- inertia coeff
				local inertiaCoeff = this.inertia/(this.inertia+this.prev.inertia)
				
				--dist = (1-(1-this.parent.stiffness)^this.parent.constrainIterations)*dist
				
				this.pos = this.pos-dir*dist*(1-inertiaCoeff)
				this.prev.pos = this.prev.pos+dir*dist*inertiaCoeff
			else
				--local dir = this.pos-this.parent:GetMainAnchor()
				
				--local dist = dir:length() - this.length
				--dir:normalize()
				local anchorPos, anchorMatrix = this.parent:GetMainAnchor(delta)
				this.pos = anchorPos
			end
		end,
	resolve = function(this)
			-- hard push out
			local push = pushOutBlock(this.pos-vec(0,this.height,0))
			this.pos = push+vec(0,this.height,0)
		end,
	render = function(this, delta, context)
			if not this.pos then return end
	
			-- find previous node position
			local anchorPos
			local anchorMatrix
			if this.prev then
				anchorPos = this.prev:getRenderEndPos(delta)
				anchorMatrix = this.prev.mat
			else
				anchorPos = this.parent.anchorPos
				anchorMatrix = this.parent.anchorMat
			end
			
			this.renderPos = anchorPos
			
			if this.next and this.part then
				-- calculate local cordinate system
				local xAxis = anchorMatrix:applyDir(1,0,0):normalize()
				local yAxis = anchorMatrix:applyDir(0,1,0):normalize()
				local anchorDir = anchorMatrix:applyDir(0,0,1):normalize()
			
				local renderDir = math.lerp(this.oldDir, this.dir, delta)
			
				local localDir = vec(xAxis:dot(renderDir),yAxis:dot(renderDir),anchorDir:dot(renderDir))
				--drawDir(vec(0,1,0), localDir, vec(0,0,0))
				
				-- rotation
				this.rot = PirOdd.DirToAngles(renderDir)
				
				if this.prev then
					-- near-perfect vertical check
					if renderDir.xz:lengthSquared() < 0.1 then
						-- inherent previous segment's yaw
						this.rot.y = this.prev.rot.y
					end
				end
				
				-- stretch
				this.renderLength = (math.lerp(this.oldPos, this.pos, delta)-math.lerp(this.next.oldPos, this.next.pos, delta)):length()
				
				-- part matrix
				local mat = matrices.mat4()
				mat:translate(-this.part:getPivot()):scale(vec(1,1,this.renderLength/this.length))
				mat:rotate(PirOdd.DirToAngles(this.parent.anchor:partToWorldMatrix():invert():applyDir(renderDir)))
				--mat:translate(this.part:getPivot())
				
				mat:translate(this.parent.anchor:partToWorldMatrix():invert():apply(anchorPos)+this.parent.anchor:getPivot())
				
				
				
				this.part:setMatrix(mat)
				--this.part:setRot(PirOdd.DirToAngles(localDir))
			end
			
			--[[drawDir(this.pos, anchorDir, vec(0,0,1))
			drawDir(this.pos, anchorMatrix:applyDir(1,0,0):normalize(), vec(1,0,0))
			drawDir(this.pos, anchorMatrix:applyDir(0,1,0):normalize(), vec(0,1,0))]]
		end
}

TailNode.__index = TailNode

function TailNode.new(parent, part,prev, index)
	local new = setmetatable({
		index = index,
	
		pos = nil,
		oldPos = vec(0,0,0),
		renderPos = vec(0,0,0),
		rot = vec(0,0,0),
		dir = vec(0,1,0),
		vel = vec(0,0,0),
		oldDir = vec(0,0,0),
		intertia = 1,
		mat = matrices.mat4(),
		anchorDir = vec(0,0,0),
		
		part = part,
		
		length = 10/16 * math.playerScale,
		height = 3.5/16 * math.playerScale,
		
		prev = prev,
		next = nil,
		
		parent = parent,
		
		enabled = true
	},TailNode)
	
	if prev then
		prev.next = new
	end
	
	if part then
		part:moveTo(parent.anchor):setVisible(true)
	end
	
	table.insert(parent.list,new)
	return new
end

local SnakeTail = {
	GetMainAnchor = function(this)
		--local angle = math.rad(player:getBodyYaw(delta)-90)

		--return player:getPos(delta)+vec(0,1,0), vec(math.cos(angle),0,math.sin(angle))
		local mat = this.anchor:partToWorldMatrix()
		local pos = mat:apply()
		
		return pos, mat:translate(-pos)
	end,
	
	SetEnabled = function(this, state)
		this.enabled = state
		
		if not state then
			-- reset positions
			for i,node in ipairs(this.list) do
				node.pos = nil
			end
		end
	end
}

SnakeTail.__index = SnakeTail

function SnakeTail.new(anchor,tailRoot)
	local new = setmetatable({
		anchor = anchor,
		anchorPos = vec(0,0,0),
		anchorMat = matrices.mat4(),
		list = {},
		
		groundFriction = 0.8,
		airFriction = 0.1,
		
		tension = 20,
		
		directPullCount = 5,
		directPullStrength = 40,
		
		maxAngle = 50,
		angleStiffness = 0.75,
		
		slitherAmp = 15,
		slitherPeriod = 20,
		slitherLengthPeriod = 0.5,
		
		currentSlither = vec(0,0,0),
		currentSlitherConst = vec(0,0,0),
		marchedDistance = 0,
		
		-- higher makes more accurate constraints, but each iteration is a nasty square root per segment
		constrainIterations = 8,
		
		enabled = true
	},SnakeTail)
	
	GenTail(tailRoot, new)
	
	function events.tick()
		if not new.enabled then return end
	
		new.marchedDistance = 0
		new.anchorMat = new.anchor:partToWorldMatrix()
		new.anchorPos = new.anchorMat:apply()
	
		local slitherAmp = math.min(player:getVelocity():length()*20,10)
		new.currentSlither = slitherAmp*new.slitherAmp
		--new.currentSlitherConst = vectors.rotateAroundAxis(-player:getBodyYaw(),vec(0,0,slitherAmp/5),vec(0,1,0))*new.slitherAmp
	
		for i,node in ipairs(new.list) do
			node:tick()
			new.marchedDistance = new.marchedDistance+node.length
		end
		for i=1,new.constrainIterations do
			for i,node in ipairs(new.list) do
				node:constrain()
			end
		end
		for i,node in ipairs(new.list) do
			node:resolve()
		end
	end
	
	local renderTail = function(delta, context)
		if not new.enabled then return end
	
		if context == "PAPERDOLL" then return end
		if delta == 1 then return end -- some contexts give a weird delta, while this is techincally normally possible, if it's somehow exactly 1 we will just disregard it
		local currTime = world:getTime(delta)
		
		-- to prevent anomalies, slowest FPS is 20
		deltaTime = math.min(currTime-prevFrame,1)
		
		if deltaTime == 0 then return end
		
		prevFrame = currTime

		new.anchorPos = new.anchor:partToWorldMatrix():apply()

		for i,node in ipairs(new.list) do
			node:render(delta, context)
		end
	end
	
	anchor.midRender = renderTail
	
	return new
end

function drawDir(pos, dir, color)
	drawVec(pos,dir:normalized(),color)
end

function drawVec(pos, dir, color)	
	for i=1,dir:length()*16 do
		particles["ash"]:pos(pos+dir*i/16):color(color):lifetime(0):scale(2):spawn()
	end
end

function GenTail(tailRoot, parent)
	local name = tailRoot:getName()
	local index = (tonumber(name:sub(name:find("%d+") or 0, -1)) or 1)+1
	name = name:gsub("%d+", "")

	local currentTail = tailRoot
	local lastNode = TailNode.new(parent, tailRoot,nil,index-1)
	
	while currentTail[name .. index] do
		currentTail = currentTail[name .. index]
		lastNode = TailNode.new(parent,currentTail,lastNode,index)
		index = index+1
	end
	
	-- final target node
	TailNode.new(parent,nil,lastNode,index)
end

LamiaTail = SnakeTail.new(models.aquatic.root.SnakeBody.SnakeBody2.TailAnchor,models.aquatic.root.SnakeBody.SnakeBody2.TailAnchor.Tail)