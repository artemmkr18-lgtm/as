require("lib.GSAnimBlend")

local anims = require("lib.EZAnims")
local fox = anims:addBBModel(animations.model)
animations.model.crouch:setBlendTime(1)
---PLAYER MODEL ADJUSTMENTS---
vanilla_model.PLAYER:setVisible(false)
vanilla_model.ARMOR:setVisible(false)
vanilla_model.HELMET_ITEM:setVisible(true)
vanilla_model.HELMET:setVisible(true)


models.model.whole.body.LeftArm.LeftItemPivot:setScale(0.9)
models.model.whole.body.RightArm.RightItemPivot:setScale(0.9)
models.model.whole.body.Body.ELYTRA_PIVOT:setScale(0.8)
models.model.whole.body.neck.head.HelmetPivot:setScale(0.9)
models.model.whole.body.neck.head.HelmetItemPivot:setScale(0.9)
nameplate.ENTITY:setPos(0,-0.3,0)
---------------------------------------------------------------------------------------------------------------
---RENDER FIRST PERSON ARM---
function events.render(delta, context)
    local firstPerson = context == "FIRST_PERSON"
    models.model.whole.body.RightArm:setVisible(not firstPerson)
    models.model.MISC.RightArm2:setVisible(firstPerson)
end

---------------------------------------------------------------------------------------------------------------
---NAMEPLATE---
--[[
nameplate.All:setText(
   toJson({
       { text = 'Fox', color = 'red' }
   })
)
]]


---------------------------------------------------------------------------------------------------------------
---IDLE BODY MOVEMENTS---
animations.model.i_1:setSpeed(0.3)
animations.model.i_1:play()
animations.model.i_2:setSpeed(0.15)
animations.model.i_2:play()
animations.model.i_3:setSpeed(0.3)
animations.model.i_3:play()
--animations.model.ears_down:play()
---------------------------------------------------------------------------------------------------------------
--- ANIMATION BLENDING ---
animations.model.sittin:setBlendTime(4)
---------------------------------------------------------------------------------------------------------------
---EMISSIVE--- 
--[[
models.model.whole:setSecondaryRenderType("NONE")
models.model.MISC:setSecondaryRenderType("NONE")
--]]

---------------------------------------------------------------------------------------------------------------
---HAIR PHYSICS---
local SwingingPhysics = require("lib.swinging_physics")

SwingingPhysics.swingOnHead(models.model.whole.body.neck.head.hair.front, 90, { -2, 5, -0, 0, -5, 5 },
    nil, 0)

    SwingingPhysics.swingOnHead(models.model.whole.body.neck.head.hair.front.front_left, 90, { -2, 5, -0, 0, -15, 5 },
    nil, 1)
    SwingingPhysics.swingOnHead(models.model.whole.body.neck.head.hair.front.front_right, 90, { -2, 5, -0, 0, -5, 15 },
    nil, 2)
SwingingPhysics.swingOnHead(models.model.whole.body.neck.head.hair.left, 90, { -10, 0, -0, 0, -15, 5 },
    nil, 1)
SwingingPhysics.swingOnHead(models.model.whole.body.neck.head.hair.right, 90, { -10, 0, -0, 0, -5, 15 },
    nil, 1)
SwingingPhysics.swingOnHead(models.model.whole.body.neck.head.hair.back, 90, { -10, 2, -0, 0, -10, 10 },
    nil, 0)

local squapi = require("lib.SquAPI")

squapi.ear:new(
    models.model.whole.body.neck.head.ears.e1, --leftEar
    models.model.whole.body.neck.head.ears.e2, --(nil) rightEar
    nil, --(1) rangeMultiplier
    false, --(false) horizontalEars
    nil, --(2) bendStrength
    nil, --(true) doEarFlick
    nil, --(400) earFlickChance
    0.05, --(0.1) earStiffness
    nil  --(0.8) earBounce
)

local chip_tail = models.model.whole.body.Body.tail

local myTail = {
    chip_tail.t1, chip_tail.t1.t2, chip_tail.t1.t2.t3, chip_tail.t1.t2.t3.t4, chip_tail.t1.t2.t3.t4.t5, chip_tail.t1.t2.t3.t4.t5.t6
}
--replace each nil with the value/parmater you want to use, or leave as nil to use default values :)
--parenthesis are default values for reference
squapi.tail:new(myTail,
    5,    --(15) idleXMovement
    10,   --(5) idleYMovement
    nil,  --(1.2) idleXSpeed
    nil,  --(2) idleYSpeed
    1,    --(2) bendStrength
    -0.5, --(0) velocityPush
    nil,  --(0) initialMovementOffset
    nil,  --(1) offsetBetweenSegments
    nil,  --(.005) stiffness
    nil,  --(.9) bounce
    nil,  --(90) flyingOffset
    nil,  --(-90) downLimit
    30    --(45) upLimit
)


