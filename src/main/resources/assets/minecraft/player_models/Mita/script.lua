local squapi = require("SquAPI")

local physBone = require('physBoneAPI')

local skirtPhysics = require("skirt_physics")

squapi.smoothHead:new(
    {
        models.model.root.Head --element(you can have multiple elements in a table)
    },
    nil,    --(1) strength(you can make this a table too)
    nil,    --(0.1) tilt
    nil,    --(1) speed
    nil     --(true) keepOriginalHeadPos
)

squapi.eye:new(
    models.model.root.Head.eyes,  --the eye element 
    nil,  --(0.25) left distance
    0.25,  --(1.25) right distance
    nil,  --(0.5) up distance
    nil   --(0.5) down distance
)

function events.entity_init()
    physBone.physBone_pony1:setGravity(-3)
    physBone.physBone_pony2:setGravity(-3)
end

--hide vanilla model
vanilla_model.PLAYER:setVisible(false)

--hide vanilla armor model
vanilla_model.ARMOR:setVisible(false)

--call skirtPhysics function
skirtPhysics.new(models.model.root.Body.Skirt)

--toggle stuff
local mainPage = action_wheel:newPage()
action_wheel:setPage(mainPage)

 -- eye toggles

function pings.emotionless(a)
    models.model.root.Head.eyes.lefteye.leftspark:setVisible(a)
    models.model.root.Head.eyes.righteye.rightspark:setVisible(a)
end

local toggleaction = mainPage:newAction()
    :title("Emotionless Eyes")
    :item("lead")
    :setOnToggle(pings.emotionless)