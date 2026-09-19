local anim_states = require("scripts.anim_states")


local conf = {}
if host:isHost() then
    config:setName("Aco")
    config:save("emissive_eyes", config:load("emissive_eyes") or false)
    config:save("helmet_render", config:load("helmet_render") or false)
    config:save("tpov", config:load("tpov") or false)
    confFile = config:load()



    for k, v in pairs(confFile) do
        conf[k] = v
    end
end
function writeConf(cname, val)
    config:save(cname, val)
end

local helmets = {models.model.whole.body.neck.head.HelmetItemPivot, models.model.whole.body.neck.head.HelmetPivot}
local helm_hair = {models.model.whole.body.neck.head.hair.aa}
local function texture_switch(array, texture)
    for i = 1, #array, 1 do
        array[i]:setPrimaryTexture("CUSTOM", texture)
    end
end


local function visibility_switch(array, boolean)
    for i = 1, #array, 1 do
        array[i]:setVisible(boolean)
    end
end

function pings.emissive_eyes_toggle()
    animations.model.blink:play()
    conf["emissive_eyes"] = not conf["emissive_eyes"]
    if host:isHost() then
        writeConf("emissive_eyes", conf["emissive_eyes"])
    end
    pushChanges()
end

function pings.short_pov_toggle()
    conf["tpov"] = not conf["tpov"]
    if host:isHost() then
        writeConf("tpov", conf["tpov"])
    end
    pushChanges()
end

function pings.helmet_toggle()
    conf["helmet_render"] = not conf["helmet_render"]
    if host:isHost() then
        writeConf("helmet_render", conf["helmet_render"])
    end
    pushChanges()
end


function pings.actionSit()
    anim_states.is_sitting = not anim_states.is_sitting
    pushChanges()
end

if host:isHost() then
    local main_page = action_wheel:newPage()
    action_wheel:setPage(main_page)
    local format = "§b§l"



    main_page:newAction()
        :title("Emmissive glow")
        :item("minecraft:glow_berries")
        :hoverColor(1, 0, 1)
        :onLeftClick(pings.emissive_eyes_toggle)
    main_page:newAction()
        :title("Short POV")
        :item("minecraft:axolotl_bucket")
        :hoverColor(1, 0, 1)
        :onLeftClick(pings.short_pov_toggle)
        main_page:newAction()
        :title("Helmet")
        :item("minecraft:iron_helmet")
        :hoverColor(1, 0, 1)
        :onLeftClick(pings.helmet_toggle)
    main_page:newAction()
        :title("Sit")
        :item("minecraft:oak_slab")
        :hoverColor(1, 0, 1)
        :onLeftClick(pings.actionSit)
end


function pushChanges()
    visibility_switch(helmets, conf["helmet_render"])
    visibility_switch(helm_hair, not conf["helmet_render"] or player:getItem(6).id == "minecraft:air")
    vanilla_model.HELMET_ITEM:setVisible(conf["helmet_render"])
vanilla_model.HELMET:setVisible(conf["helmet_render"])


    if conf["emissive_eyes"] then
        models.model.whole.body.neck.head.eyes:setSecondaryRenderType("EMISSIVE")
    else
        models.model.whole.body.neck.head.eyes:setSecondaryRenderType("NONE")
    end

    if host:isHost() then
        if conf["tpov"] then
            renderer:setEyeOffset(0, 0, 0)
            renderer:setOffsetCameraPivot(0, 0, 0)
        else
                if animations.model.sittin:isPlaying() then
                    renderer:setEyeOffset(0, -0.7, 0)
                    renderer:setOffsetCameraPivot(0, -0.7, 0)
                else
                    renderer:setEyeOffset(0, -0.3, 0)
                    renderer:setOffsetCameraPivot(0, -0.3, 0)
                end
        end
    end
end

function pings.pushConfig(targetConf)
    if not host:isHost() then
        if targetConf ~= nil then
            --print("Received config:", targetConf)
            local cfg = targetConf:sub(1, string.len(targetConf) - 2)
            local type = targetConf:sub(#targetConf - 1, #targetConf - 1)
            if type == 'b' then
                local val = (0 ~= tonumber(targetConf:sub(#targetConf, #targetConf)))
                --print(cfg, "=", val)
                conf[cfg] = val
            elseif type == 'n' then
                local val = tonumber(targetConf:sub(#targetConf, #targetConf))
                --print(cfg, "=", val)
                conf[cfg] = val
            else
                print("Corrupt ping has been received! \"", targetConf, "\"")
            end
        end
    end
end


local pingTimer = 0
function events.tick(delta, context)
    if pingTimer < 20 then
        pingTimer = pingTimer + 1
    else
        pingTimer = 0
        if host:isHost() then
            for k, v in pairs(conf) do
                local payload
                if type(v) == "boolean" then
                    if v then
                        payload = k .. "b" .. "1"
                    else
                        payload = k .. "b" .. "0"
                    end
                elseif type(v) == "number" then
                    if v < 10 then
                        payload = k .. "n" .. v
                    else
                        print("conf[", k, "] is bigger than 9, changed it to 9 instead")
                        payload = k .. "n" .. 9
                        conf[k] = 9
                    end
                else
                    print(
                        "Currently single digit number and boolean are the only data types supported by this script. Data type of conf[\"",
                        k, "\"] is ", type(v))
                end
                pings.pushConfig(payload)
            end
        end
        pushChanges()
    end
end

