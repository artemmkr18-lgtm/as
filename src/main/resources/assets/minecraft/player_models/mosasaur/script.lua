vanilla_model.ALL:setVisible(false)
vanilla_model.HELD_ITEMS:setVisible(true)
SIZE = 1
local mosasaur = models.mosasaur.root
local helicopter = models.mosasaur.helicopter
helicopter:setVisible(false)
local squapi = require("SquAPI")
local myTail = {
    mosasaur.tail_a;
    mosasaur.tail_a.tail_b;
    mosasaur.tail_a.tail_b.tail_c;
    mosasaur.tail_a.tail_b.tail_c.tail_d;
    mosasaur.tail_a.tail_b.tail_c.tail_d.tail_e;
    mosasaur.tail_a.tail_b.tail_c.tail_d.tail_e.tail_f;
    mosasaur.tail_a.tail_b.tail_c.tail_d.tail_e.tail_f.tail_g;
}
squapi.tail:new(myTail,
    7.5,    --(15) idleXMovement
    5,    --(5) idleYMovement
    1,    --(1.2) idleXSpeed
    0.75,    --(2) idleYSpeed
    1,    --(2) bendStrength
    0,    --(0) velocityPush
    0,    --(0) initialMovementOffset
    1,    --(1) offsetBetweenSegments
    0.005,    --(.005) stiffness
    0.9,    --(.9) bounce
    90,    --(90) flyingOffset
    -20,    --(-90) downLimit
    20     --(45) upLimit
)

local headY = 0
local bodyY = 0
local Ydiff = 0
local headX = 0
local bodyX = 0
local Xdiff = 0
local Larm = 0
local Rarm = 0
local PREVIOUSX = 0
local CURRENTX = 0
local PREVIOUSY = 0
local CURRENTY = 0
local watercounter = 50

local crawl = animations.mosasaur.crawl
local crawl_still = animations.mosasaur.crawl_still

function events.tick()
headY = (vanilla_model.HEAD:getOriginRot().y + 180) % 360 - 180
Ydiff = (headY - CURRENTY + 180) % 360 - 180

headX = (vanilla_model.HEAD:getOriginRot().x + 180) % 360 - 180
Xdiff = ((headX - CURRENTX + 180) % 360 - 180)

PREVIOUSX = CURRENTX
CURRENTX = ((math.lerp(headX, Xdiff, 0.1))/8)*-1
PREVIOUSY = CURRENTY
CURRENTY = (math.lerp(headY, Ydiff, 0.1))/5

if crawl_still:isPlaying() or crawl:isPlaying() then
    CURRENTX = 0.1
end

Larm = Xdiff *5
Rarm = (Xdiff *5)*-1
local isinwater = player:isInWater()
if not isinwater  and HELICOPTER_ACTIVE == true then
    if watercounter >= 0 then
    watercounter = watercounter - 1
    end
    if watercounter <= 0 then
        helicopter:setVisible(true)
        helicopter:setPos(0,15,0)
        mosasaur: setPos(0,16,0)
        CURRENTX = 0.1
        CURRENTY = 0.1
    end
else
    helicopter:setVisible(false)
    helicopter:setPos(0,0,0)
    mosasaur: setPos(0,0,0)
end
if isinwater == true then
helicopter:setVisible(false)
helicopter:setPos(0,0,0)
mosasaur: setPos(0,0,0)
watercounter = 50
end
end

function events.render(delta)
    -- Interpolate between the two ticks to prevent jittery movements
local smoothX = math.lerp(PREVIOUSX, CURRENTX, delta)
local smoothY = math.lerp(PREVIOUSY, CURRENTY, delta)

mosasaur.torso:setRot(smoothX,smoothY,0)
mosasaur.torso.torso2:setRot(smoothX,smoothY,0)
mosasaur.torso.torso2.torso3:setRot(smoothX,smoothY,0)
mosasaur.torso.torso2.torso3.torso4:setRot(smoothX,smoothY,0)
mosasaur.torso.torso2.torso3.torso4.neck:setRot(smoothX,smoothY,0)
mosasaur.torso.torso2.torso3.torso4.neck.head:setRot(smoothX,smoothY,0)
local isinwater = player:isInWater()

if isinwater ~= true then
mosasaur.torso.torso2.torso3.torso4.left_arm:setRot(0,0,(smoothX) *6)
mosasaur.torso.torso2.torso3.torso4.right_arm:setRot(0,0,((smoothX)*6)*-1)
else
mosasaur.torso.torso2.torso3.torso4.left_arm:setRot(0,0,0)
mosasaur.torso.torso2.torso3.torso4.right_arm:setRot(0,0,0)
end
mosasaur:setScale(SIZE,SIZE,SIZE)
helicopter:setScale(SIZE,SIZE,SIZE)
local current_headpiece = player:getItem(6):getID()
if current_headpiece:find("head") or current_headpiece:find("skull") or current_headpiece:find("carved_pumpkin") then
    vanilla_model.HELMET:setVisible(true)
else
    vanilla_model.HELMET:setVisible(false)
end
end