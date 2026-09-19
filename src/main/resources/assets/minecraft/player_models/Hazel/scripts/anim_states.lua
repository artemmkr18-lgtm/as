
local anim_states = {}
anim_states.is_sitting = false

local ping_timer = 0
local forVel, vertVel, sumVel
---------------------------------------------------------------------------------------------------------------
---IS MOVING CHECK---
function events.tick()

    forVel = player:getVelocity():dot((player:getLookDir().x_z):normalize())
    vertVel = (player:getVelocity() * matrices.rotation3(0, player:getRot().y, 0)).y
    sumVel = math.sqrt(forVel * forVel + vertVel * vertVel)


end

---------------------------------------------------------------------------------------------------------------
---STATE ANIM PLAY---

function events.render()

       if ping_timer < 5 then
        ping_timer = ping_timer + 1
    else
        ping_timer = 0
             animations.model.sittin:setPlaying(anim_states.is_sitting)
             if sumVel > 0 then
                anim_states.is_sitting = false
             end
    end
end

---------------------------------------------------------------------------------------------------------------
---IDLE FLICKS---

local randanim = {}
randanim[1] = animations.model.blink
randanim[2] = animations.model.blink2
function events.tick()
    if math.random(0, 500) == 0 and not animations.model.blink2:isPlaying() then
            randanim[1]:play()
    end
        if math.random(0, 400) == 0 and not animations.model.blink:isPlaying() then
            randanim[2]:play()
    end
end
---------------------------------------------------------------------------------------------------------------

return anim_states