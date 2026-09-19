-- v0.13
-- wrap function doesn't suck anymore
-- improvements to multi-state and pose

local actionHelper = {}
local posing = false

function actionHelper.toggle(page, name, func, pickyInit, defaultState, prePing, noSave, noPing)
	local obj = {
		func = func,
		preping = prePing,
		state = config:load(name) or nil,
	}
	
	if not noPing then
		pings[name] = function(state)
			obj.state = state
			if obj.func then
				obj.func(state, true)
			end
		end
		
		obj.ping = pings[name]
	end
	
	local function actionFunc(state, action, noping)	
		if prePing then
			-- preping must return true to proceed
			if not prePing() then
				-- reset toggle and break
				obj.action:setToggled(obj.state)
				return
			end
		end
	
		obj.state = state
		if obj.func then
			if noping or not obj.ping then
				obj.func(state)
			end
		end
		
		if obj.ping then
			if not noping then
				obj.ping(state)
			end
		end
		
		if host:isHost() then
			obj.action:setToggled(obj.state)
			if not noSave then
				config:save(name,obj.state)
			end
		end
	end
	
	-- action wheel stuff
	if host:isHost() then
		obj.action = page:newAction()
			:setTitle("Enable " .. name)
			:setToggleTitle("Disable " .. name)
		obj.action:setOnToggle(actionFunc)
		
		-- init
		if not pickyInit or config:load(name) then
			local initState = nil
			if not noSave then
				initState = config:load(name)
			end
			if initState == nil then
				initState = defaultState
			end
			actionFunc(initState, obj.action, true)
		end
	end
	
	return obj
end

local poses = {}

function actionHelper.pose(page, name, anim, extrafunc)
	local obj = {
		state = false,
		extrafunc = extrafunc
	}
	
	obj.func = function(state, skipcheck)
		if not skipcheck then
			if posing then
				for i,pose in ipairs(poses) do
					if pose.state then
						pose.func(false, true)
					end
				end
			end
		end
	
		posing = state
		obj.state = state
		anim:setPlaying(state)
		
		if host:isHost() then
			obj.action:setToggled(state)
		end
		
		if obj.extrafunc then
			obj.extrafunc(state)
		end
	end
	
	obj.ping = function(state)
		obj.state = state
		obj.func(state)
	end
	
	pings[name] = obj.ping
	
	if host:isHost() then
		obj.action = page:newAction()
			:setTitle(name)
		obj.action:setOnToggle(pings[name])
	end
	
	table.insert(poses, obj)
	
	return obj
end

function wrap(value, maximum)
	return (value-1) % maximum+1
end

function actionHelper.multi(page, name, max_val, func, titles, defaultState, prePing)
	local obj = {
		func = func,
		preping = prePing,
		max = max_val,
		state = config:load(name) or 1,
		newState = 1,
		
		titles = titles or {},
	}
	
	--compact = true
	
	pings[name] = function(state)
		obj.state = state
		obj.func(state, true)
	end
	
	obj.ping = pings[name]
	
	local function scroll(dir)
		if dir ~= 0 then
			dir = -math.sign(dir)
			obj.newState = wrap(obj.newState+dir, obj.max)			
			
			if player:isLoaded() then
				sounds["ui.button.click"]:volume(0.2):pitch(1.5):pos(player:getPos()):stop():play()
			end
		end
		
		local titleCurrent = obj.titles[obj.state] or name .. " " .. obj.state
		local titleNew = obj.titles[obj.newState] or name .. " " .. obj.state
		
		local scrollDesc = "\n"
		
		if compact then
			scrollDesc = "\n\n|§7" .. (obj.titles[wrap(obj.newState-1, obj.max)] or name .. " " .. obj.state)
				.. "\n§a>§r" .. titleNew
				.. "\n|§7" .. (obj.titles[wrap(obj.newState+1, obj.max)] or name .. " " .. obj.state)
		else
			for i=1,max_val,1 do
				if obj.newState == i then
					scrollDesc = scrollDesc .. "\n§a>§r"
				elseif obj.state == i then
					scrollDesc = scrollDesc .. "\n§a|§r"
				else
					scrollDesc = scrollDesc .. "\n§7|§r"
				end
				scrollDesc = scrollDesc .. (obj.titles[i] or name .. " " .. obj.state)
			end
		end
		
		if obj.newState ~= obj.state then
			scrollDesc = scrollDesc .. "\n§7Click to confirm"
			obj.action:setHoverColor(0.8,0.5,0.5)
		else
			scrollDesc = scrollDesc .. "\n§7Scroll to select"
			obj.action:setHoverColor(0.5,0.5,0.5)
		end
		
		obj.action:setTitle(name .. ": " .. titleCurrent .. scrollDesc)
	end
	
	local function actionFunc(action, noping)
		-- failsafe
		if obj.newState < 1 or obj.newState > max_val then
			obj.newState = 1
			print("State for action \"" .. name .. "\" was out of bounds, resetting value")
		end
	
		obj.state = obj.newState
		if prePing then
			-- preping must return true to proceed
			if not prePing() then
				-- reset toggle and break
				obj.action:setToggled(obj.state)
				return
			end
		end
	
		if obj.func then
			if noping or not obj.ping then
				obj.func(obj.state)
			end
		end
		
		if obj.ping then
			if not noping then
				obj.ping(obj.state)
			end
		end
		
		if host:isHost() then
			scroll(0)
			config:save(name,obj.state)
		end
	end
	
	if host:isHost() then
		obj.action = page:newAction()
			:setTitle(name)
			:setColor(1,0,0)
		obj.action:setOnLeftClick(actionFunc):setOnScroll(scroll)
		
		-- init
		local initState = config:load(name)
		
		-- reset the saved value just in case the saved one causes a crash on init for some reason
		config:save(name, 1)
		
		-- failsafe for nil saved value
		if initState == nil then
			initState = defaultState or 1
		end
		
		-- apply state
		obj.newState = initState
		actionFunc(obj.action, true)

		scroll(0)
	end
	return obj
end

function events.tick()
	if posing then
		if player:getVelocity():lengthSquared() > 0.01 or player:isCrouching() then
			for i,pose in ipairs(poses) do
				if pose.state then
					pose.func(false)
				end
			end
		end
	end
end

return actionHelper