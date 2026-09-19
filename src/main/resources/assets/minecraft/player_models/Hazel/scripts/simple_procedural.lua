---------------------------------------------------------------------------------------------------------------
---SMOOTH BODY MOVEMENT AND EYES---

local potato = {}

local _rot              -- recieved head rotation
local rot = { 0, 0, 0 } -- smooth head rotation



function GetRotations()
    -- SMOOTHING HEAD ROTATION
    rot[1] = math.lerp(rot[1], _rot.x, 0.1)
    rot[2] = math.lerp(rot[2], _rot.y, 0.1)
    rot[3] = math.sin(rot[2] / 80) * 10
end

function events.tick()
    _rot = (vanilla_model.HEAD:getOriginRot() + 180) % 360 - 180
end

function events.render(delta)
    GetRotations()
end

function potato:RotationApply(xmod, ymod, zmod)
    return {rot[1] * xmod, rot[2] * ymod, rot[3] * zmod}
end

function potato:RotationMovement(xmod, ymod, zmod)
    return {-rot[2] * xmod, rot[1] * ymod, rot[1] * zmod}
end

function set_rot(modelpart, var1, var2)
    local sum = vec(0, 0, 0)
    var1 = var1 or vec(0, 0, 0)
    var2 = var2 or vec(0, 0, 0)


    sum[1] = var1[1] + var2[1]
    sum[2] = var1[2] + var2[2]
    sum[3] = var1[3] + var2[3]

    modelpart:setRot(sum)
end

function set_pos(modelpart, var1, var2)
    local sum = vec(0, 0, 0)
    var1 = var1 or vec(0, 0, 0)
    var2 = var2 or vec(0, 0, 0)


    sum[1] = var1[1] + var2[1]
    sum[2] = var1[2] + var2[2]
    sum[3] = var1[3] + var2[3]

    modelpart:setPos(sum)
end
local skirt = models.model.whole.body.Body.skirt
local eyeTarget = models.model.whole.body.neck.head.eyes
local eyeTargetPos = eyeTarget.iris:getPos()
local eyeTargetPos2 = eyeTarget.iris2:getPos()
local eyeTargetMul = 0.01
local eyeLimX = { 0, 0 }
eyeLimX.min = -0.1
eyeLimX.max = 0.4
local eyeLimY = { 0, 0 }
eyeLimY.min = -0.5
eyeLimY.max = 0.5

function events.tick()
    eyeTargetPos.x = math.clamp(1.57 * math.sin(-_rot.y * eyeTargetMul), eyeLimX.min, eyeLimX.max)
    eyeTargetPos.y = math.clamp(_rot.x * eyeTargetMul, eyeLimY.min, eyeLimY.max)
    eyeTargetPos2.x = math.clamp(1.57 * math.sin(-_rot.y * eyeTargetMul), -eyeLimX.max, -eyeLimX.min)
    eyeTargetPos2.y = eyeTargetPos.y
end

function potato:EyeMovement(modelpart, delta)
    modelpart.iris:setPos(math.lerp(eyeTarget.iris:getPos(), eyeTargetPos, delta))
    modelpart.iris2:setPos(math.lerp(eyeTarget.iris2:getPos(), eyeTargetPos2, delta))
end


function events.render(delta)
    set_rot(models.model.whole.body.neck.head, potato:RotationApply(0.5, 0.5, 1))
    set_rot(models.model.whole.body.neck, potato:RotationApply(0.2, 0.2, 0))
    set_rot(models.model.whole.body, potato:RotationApply(0.3, 0.3, -1.5))
    set_rot(models.model.whole, potato:RotationApply(-0.05, 0.04, 0))

    set_pos(models.model.whole.body, potato:RotationMovement(0.01, 0, 0))
    set_pos(models.model.whole.body.neck, potato:RotationMovement(0, 0, -0.01))
    set_pos(models.model.whole.body.neck.head, potato:RotationMovement(0, 0, 0.01))
    set_pos(models.model.whole.body.neck.head.eyes.lashes, potato:RotationMovement(0, 0.007, 0))


    potato:EyeMovement(models.model.whole.body.neck.head.eyes, delta)
end


--------------------------------------------------------------------------------------------------------------
---ADDITIONAL MOVEMENT---
function events.render(delta, context)
    local rot_r = vanilla_model.RIGHT_LEG:getOriginRot()

    models.model.whole.RightLeg:setRot(-rot_r * 0.3)
    models.model.whole.LeftLeg:setRot(rot_r * 0.3)

    models.model.whole.body.LeftArm:setRot(-rot_r * 0.2)
    models.model.whole.body.RightArm:setRot(rot_r * 0.2)

    models.model.whole:setPos(nil, math.abs(rot_r.x / 70) * 1.5, nil)

end

return potato
