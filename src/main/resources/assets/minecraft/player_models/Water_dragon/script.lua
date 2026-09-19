vanilla_model.PLAYER:setVisible(false)
vanilla_model.ARMOR:setVisible(false)
vanilla_model.ELYTRA:setVisible(false)

function events.TICK()
    local time = world.getTimeOfDay()

    local isNight = time >= 13000 and time < 23000
    local isWet = player:isWet()

    local state = (isNight or isWet)

    if lastState ~= state then
        lastState = state
        pings.syncVisibility(state)
    end
end

 function pings.syncVisibility(state)
    local v = state

    models.model.all.root.bodysegment1.glow18:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.glow5:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.glow6:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.glow7:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.glow8:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.glow9:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.glow10:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.glow11:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.glow12:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.glow13:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.glow14:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.tail7.glow15:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.tail7.tail8.glow16:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.tail7.tail8.tail9.glow17:setVisible(v)

    models.model.all.root.NeckSegment3.glow4:setVisible(v)
    models.model.all.root.NeckSegment3.NeckSegment2.glow3:setVisible(v)
    models.model.all.root.NeckSegment3.NeckSegment2.NeckSegment1.glow2:setVisible(v)
    models.model.all.root.NeckSegment3.NeckSegment2.NeckSegment1.Heead.glow:setVisible(v)

    models.model.all.root.bodysegment1.bone31:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bone32:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bone33:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bone34:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.bone35:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.bone36:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.bone37:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.bone38:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.bone39:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.bone40:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.bone41:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.tail7.bone42:setVisible(v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.tail7.tail8.bone43:setVisible(v)

    models.model.all.root.NeckSegment3.bone28:setVisible(v)
    models.model.all.root.NeckSegment3.NeckSegment2.bone25:setVisible(v)
    models.model.all.root.NeckSegment3.NeckSegment2.NeckSegment1.bone22:setVisible(v)
    models.model.all.root.NeckSegment3.NeckSegment2.NeckSegment1.Heead.bone19:setVisible(v)

    models.model.all.root.bodysegment1.bone48:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bone49:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bone50:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bone51:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.bone52:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.bone53:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.bone54:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.bone55:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.bone56:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.bone57:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.bone58:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.tail7.bone59:setVisible(not v)
    models.model.all.root.bodysegment1.bodysegment2.bodysegment3.bodysegment4.bodysegment5.tail1.tail2.tail3.tail4.tail5.tail6.tail7.tail8.bone60:setVisible(not v)

    models.model.all.root.NeckSegment3.bone47:setVisible(not v)
    models.model.all.root.NeckSegment3.NeckSegment2.bone46:setVisible(not v)
    models.model.all.root.NeckSegment3.NeckSegment2.NeckSegment1.bone45:setVisible(not v)
    models.model.all.root.NeckSegment3.NeckSegment2.NeckSegment1.Heead.bone44:setVisible(not v)
end

local ns_frames = {
    vec(0, 0),
    vec(1/3, 0),
    vec(2/3, 0),

    vec(0, 1/3),
    vec(1/3, 1/3),
    vec(2/3, 1/3),

    vec(0, 2/3),
    vec(1/3, 2/3)
}

local frame = 1	
local timer = 0

function events.tick()
    timer = timer + 1

    if timer >= 3 then
        timer = 0
        frame = (frame % #ns_frames) + 1
    end

    models.model.all.root.NeckSegment3.NeckSegment2.NeckSegment1.Heead.bone18:setUV(ns_frames[frame])
end


--== smoove
local squapi = require("SquAPI")

squapi.smoothHead:new(
    {
        models.model.all.root.NeckSegment3,
        models.model.all.root.NeckSegment3.NeckSegment2,
        models.model.all.root.NeckSegment3.NeckSegment2.NeckSegment1,
        models.model.all.root.NeckSegment3.NeckSegment2.NeckSegment1.Heead --element(you can have multiple elements in a table)
    },
    1,    --(1) strength(you can make this a table too)
    nil,    --(0.1) tilt
    nil,    --(1) speed
    false     --(true) keepOriginalHeadPos
)
