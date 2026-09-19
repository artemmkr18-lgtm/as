local right_item_pivot = models.mosasaur.root.torso.torso2.torso3.torso4.neck.head.RightItemPivot
local left_item_pivot = models.mosasaur.root.torso.torso2.torso3.torso4.neck.head.LeftItemPivot

local walk_land = animations.mosasaur.walk
local walk_land_heli = animations.mosasaur.walk_heli
local crouch_walk_heli = animations.mosasaur.crouch_walk_heli
local sprint = animations.mosasaur.sprint
local sprint_heli = animations.mosasaur.sprint_heli
local idle_land = animations.mosasaur.idle_land
local heli_idle = animations.mosasaur.heli_idle
local idle_land_heli = animations.mosasaur.idle_land_heli
local crouch_land_heli = animations.mosasaur.crouch_land_heli
local crawl = animations.mosasaur.crawl
local crawl_still = animations.mosasaur.crawl_still

local swim = animations.mosasaur.swim
local idle_water = animations.mosasaur.idle_water
local walk_water = animations.mosasaur.walk_water
local riptide_fling = animations.mosasaur.riptide_fling

local elytra = animations.mosasaur.flight
elytra:setPriority(1)
local climbing = animations.mosasaur.climbing

local consuming_Right = animations.mosasaur.consumeRight
local consuming_Left = animations.mosasaur.consumeLeft
local right_swing = animations.mosasaur.right_swing
--local left_swing = animations.mosasaur.left_swing
local blockRight = animations.mosasaur.blockRight
local blockLeft = animations.mosasaur.blockLeft

local wasAirborne = false
local jumpThreshold = 0.08
function events.tick()
    local crouching = player:getPose() == "CROUCHING"
    local elytra_flight = player:getPose() == "FALL_FLYING"
    local riptide = player:getPose() == "SPIN_ATTACK"
    local y_velocity = player:getVelocity().y
    local walking = player:getVelocity().xz:length() > .01
    local isclimbing = player:isClimbing()
    local sprinting = player:isSprinting()
    local swimming = player:isVisuallySwimming()
    local inwater = player:isInWater()
    local rightarmswing = player:isSwingingArm()
    local isConsuming = player:getActiveItem():getUseAction() == "EAT" or player:getActiveItem():getUseAction() == "DRINK"
    local blocking = player:getActiveItem():getUseAction() == "BLOCK"
    local activeHand = player:getActiveHand()
    local crawling = player:getPose("SWIMMING")


    idle_land:setPlaying(not walking and not crouching and not inwater and not elytra_flight )
    idle_land_heli:setPlaying(not walking and not crouching and not inwater and not elytra_flight and HELICOPTER_ACTIVE==true)
    crouch_land_heli:setPlaying(crouching and not walking and not inwater and HELICOPTER_ACTIVE==true)
    heli_idle:setPlaying(not walking)

    walk_land:setPlaying(walking and not crouching and not crawling and not sprinting and not inwater and not elytra_flight and HELICOPTER_ACTIVE==false)
    walk_land_heli:setPlaying(walking and not crouching and not sprinting and not inwater and not elytra_flight and HELICOPTER_ACTIVE==true)
    crouch_walk_heli:setPlaying(walking and crouching and HELICOPTER_ACTIVE==true and not sprinting and not inwater and not elytra_flight)
    sprint:setPlaying(sprinting and not crouching and not inwater and HELICOPTER_ACTIVE==false)
    sprint_heli:setPlaying(sprinting and not crouching and not inwater and HELICOPTER_ACTIVE==true)


    elytra:setPlaying(elytra_flight and not inwater)
    climbing:setPlaying(isclimbing and (y_velocity > 0.1 or y_velocity <-0.1))
    crawl:setPlaying(swimming and walking and not inwater)
    crawl_still:setPlaying(swimming and not walking and not inwater)

    swim:setPlaying(inwater and swimming and not crouching)
    idle_water:setPlaying(inwater and not swimming)
    walk_water:setPlaying(inwater and walking and not swimming)
    riptide_fling:setPlaying(riptide and not elytra_flight)

    consuming_Right:setPlaying(isConsuming and activeHand == "MAIN_HAND")
    consuming_Left:setPlaying(isConsuming and activeHand == "OFF_HAND")
    right_swing:setPlaying(rightarmswing)

    blockLeft:setPlaying(blocking and activeHand == "OFF_HAND")
    blockRight:setPlaying(blocking and activeHand == "MAIN_HAND")

--left_swing:setPlaying(leftarmswing)

    local yVelocity = player:getVelocity().y
    local isOnGround = (yVelocity == 0)
    local isAirborne = not player:isClimbing() and not player:isOnGround() and not player:isInWater()--and not player:getAbilities().flying
    
    -- In Minecraft, when standing still or running on flat ground, y velocity is 0
    -- Detect the exact moment the jump starts
    if isAirborne and not wasAirborne and yVelocity > jumpThreshold then
        -- Put your jump action/animation trigger here!

        --print("Player jumped!")
    end
    -- Update the state for the next tick
    wasAirborne = isAirborne

end
function events.item_render(item)
if player:isLoaded() then
local MainHand = player:getHeldItem():getID()
local OffHand = player:getHeldItem(true):getID()
    if MainHand == "minecraft:crossbow" then
        right_item_pivot:setRot(90,195,0)
        right_item_pivot:setPos(2.5,0,0)
    else
        right_item_pivot:setRot(90,0,90)
        right_item_pivot:setPos(0,0,0)
    end
    if OffHand == "minecraft:crossbow" then
        left_item_pivot:setRot(-90,15,0)
        left_item_pivot:setPos(-0.5,0,0)
    else

        left_item_pivot:setRot(-90,0,90)
        left_item_pivot:setPos(0,0,0)
    end
end
end

