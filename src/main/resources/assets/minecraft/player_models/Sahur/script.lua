-- Auto generated script file --

--hide vanilla model
vanilla_model.PLAYER:setVisible(false)

--hide vanilla armor model
vanilla_model.ARMOR:setVisible(false)

--re-enable the helmet item
vanilla_model.HELMET_ITEM:setVisible(false)

--hide vanilla cape model
vanilla_model.CAPE:setVisible(false)

--hide vanilla elytra model
vanilla_model.ELYTRA:setVisible(false)

-- PIXELAR
function events.tick()
	local idle = not walk
	local walk = player:getVelocity().xz:length() > .01
	local is_atk = animations.model.attack:isPlaying()
	
	animations.model.idle:setPlaying(idle)
	animations.model.walk:setPlaying(walk)
	if player:isSwingingArm() and not hold_item then
		animations.model.attack:play()
	end
end