vanilla_model.PLAYER:setVisible(false)
vanilla_model.ARMOR:setVisible(false)
vanilla_model.CAPE:setVisible(false)
vanilla_model.ELYTRA:setVisible(false)

bug = models.model.bug
head = bug.Torso.AHead
abdomen = bug.Torso.Abdomen

local blend = require("lib/GSAnimBlend")

animations.model.crouchwalk:setSpeed(0.75)
animations.model.emote_sit:setPriority(10)
animations.model.holdR:setPriority(2)
animations.model.emote_sit:setBlendTime(4)

function pings.playEmote2(name)
	if player:isLoaded() then
		if animations.model[name] then
			if animations.model[name]:isPlaying() then
				animations.model[name]:stop()
			else
				animations.model[name]:play()
			end
		end
	end
end

function events.tick()
	 local headRot = (vanilla_model.HEAD:getOriginRot()+180)%360-180
	 head:setRot(
		headRot.x/2,
		headRot.y,
		headRot.z
	)
	abdomen:setRot(
		headRot.x/2,
		-headRot.y/3,
		headRot.z
	)
end


events.RENDER:register(function (delta, ctx)
    if ctx == "RENDER" then
        models.model:setPos(0,(player:isCrouching() and 2.14 or 0))
    end
end)

function events.ON_PLAY_SOUND(id, pos, vol, pitch, loop, cat, path)
    if not path then return end
    if not player:isLoaded() then return end
    if (player:getPos() - pos):length() > 0.05 then return end
  
    if id:find(".hurt") then
        sounds:playSound("entity.silverfish.hurt", pos, vol, pitch-0.5)
        return true 
    end
	 if id:find(".death") then
        sounds:playSound("entity.silverfish.death", pos, vol, pitch-0.5)
        return true 
    end
end

local mainPage = action_wheel:newPage()
action_wheel:setPage(mainPage)

mainPage:newAction(7)
:title("Sit")
:item("oak_stairs")
:onLeftClick(
function()	
	pings.playEmote2("emote_sit")
end)

mainPage:newAction(6)
:title("Threaten")
:item("red_wool")
:onLeftClick(
function()	
	pings.playEmote2("emote_threaten")
end)