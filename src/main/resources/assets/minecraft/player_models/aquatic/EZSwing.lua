-- Made by JimmyHelp
-- V5

-- Edited by PirateSee
-- changed the item name predicate to instead take in an arbitrary function
-- used because the staff on this thing represents nearly any tool

local batterUp = {}

local swinging
local isSwing
local currentTarget = world.newBlock("air")
local oldTarget = currentTarget
local olderTarget = oldTarget
function events.tick()
    swinging = player:getSwingTime() == 1
    isSwing = player:isSwingingArm()
    olderTarget = oldTarget
    oldTarget = currentTarget
    currentTarget = player:getTargetedBlock(true, player:getGamemode() == "CREATIVE" and 5 or 4.5)
end
    
local function getSwing(arm)
    if arm == "right" then
        return (player:getSwingArm() == (player:isLeftHanded() and "OFF_HAND" or "MAIN_HAND")) and  player:getPose() ~= "SLEEPING"
    elseif arm == "left" then
        return (player:getSwingArm() == (not player:isLeftHanded() and "OFF_HAND" or "MAIN_HAND")) and player:getPose() ~= "SLEEPING"
    else
        return true
    end
end

local function getItem(itemid,handy)
    if #itemid == 0 then return true end
    if handy == "right" then
        local getRight = player:getHeldItem(player:isLeftHanded())
        for key, item in pairs(itemid) do
            if getRight.id:find(item) or getRight:getName():find(item) then
                return true
            end
        end
        return false
    elseif handy == "left" then
        local getLeft = player:getHeldItem(not player:isLeftHanded())
        for key, item in pairs(itemid) do
            if getLeft.id:find(item) or getLeft:getName():find(item) then
                return true
            end
        end
        return false
    else
        return getItem(itemid,getSwing("right") and "right" or "left")
    end
end

local hitBlock
local empties = {
    ["minecraft:player_head"] = true,
    ["minecraft:zombie_head"] = true,
    ["minecraft:creeper_head"] = true,
    ["minecraft:piglin_head"] = true,
    ["minecraft:dragon_head"] = true,
    ["minecraft:skeleton_skull"] = true,
    ["minecraft:wither_skeleton_skull"] = true,
    ["minecraft:barrier"] = true,
}
local function isMining(mine)
    local blockSuccess, blockResult = pcall(olderTarget.getTextures, olderTarget)
    if blockSuccess then 
        hitBlock = not (next(blockResult) == nil) or empties[olderTarget.id]
    else 
        hitBlock = true 
    end
    if mine == "mine" then
        return hitBlock
    elseif mine == "attack" then
        return not hitBlock
    else
        return true
    end
end

local rand = math.random
local function getRandom(length,prev)
    local rResult = rand(length)
    if rResult == prev then
        return getRandom(length,prev)
    else
        return rResult
    end
end

local function checkTable(table)
    if type(table) ~= "table" then
        error("§aCustom Script Warning: §6The value provided for the first param is not a table.§c",3)
    end
    if #table < 2 then
        error("§aCustom Script Warning: §6The animation table provided is too small, it must contain at least 2 animations, and it currently has ".. #table..". \n".."If your table appears to have multiple animations in it, make sure their names/paths are correct.§c",3)
    end
    for key, value in pairs(table) do
        if type(value) ~= "Animation" then
            error("§aCustom Script Warning: §6At index "..key.." of the provided table, the value is not an animation.§c",3)
        end
    end
end

---@param anims table
---@param hand string
---@param item function
---@param mine string
---@param length boolean
---@param reset number
function batterUp:addChainedSwings(anims,hand,item,mine,length,reset)
    checkTable(anims)
    local chain = #anims
    local timer = 0
	
    function events.tick()
        if swinging then
            if getSwing(hand) and item(player:getHeldItem(hand == "left" and player:isLeftHanded())) and isMining(mine) then
                anims[chain]:stop()
                chain = (chain % #anims) + 1
                anims[chain]:restart()
                timer = 0
            end
        end
        if isSwing and length then
            anims[chain]:speed((anims[chain]:getLength()*20)/player:getSwingDuration())
        end
        if reset ~= nil then
            timer = timer + 1
            if timer == reset then
                chain = #anims
            end
        end
    end
    return self
end



---@param anims table
---@param hand string
---@param item string | table
---@param mine string
---@param length boolean
function batterUp:addRandomSwings(anims,hand,item,mine,length)
    checkTable(anims)
    local prev = rand(#anims)
    local result = #anims
    local item = type(item)=="table" and item or {item}
    function events.tick()
        if swinging then
            if getSwing(hand) and getItem(item,hand) and isMining(mine) then
                result = getRandom(#anims,prev)
                anims[prev]:stop()
                anims[result]:restart()
                prev = result
            end
        end
        if isSwing and length then
            anims[result]:speed((anims[result]:getLength()*20)/player:getSwingDuration())
        end
    end
    return self
end

---@param anims table
---@param hand string
---@param item string | table
---@param mine string
---@param length boolean
function batterUp:addSingleSwing(anim,hand,item,mine,length)
    if type(anim) ~= "Animation" then error("§aCustom Script Warning: §6The value provided for the first param is not an animation.§c",3) end
    local item = type(item)=="table" and item or {item}
    function events.tick()
        if swinging then
            if getSwing(hand) and getItem(item,hand) and isMining(mine) then
                anim:restart()
            end
        end
        if isSwing and length then
            anim:speed((anim:getLength()*20)/player:getSwingDuration())
        end
    end
    return self
end

return batterUp