HELICOPTER_ACTIVE = false

local mainPage = action_wheel:newPage()
action_wheel:setPage(mainPage)

function pings.toggle_heli(state)
    HELICOPTER_ACTIVE = state
end
function pings.size_change(size_state)
        SIZE = 1
    if size_state == true then
        SIZE = 3
    end
end

local toggleaction = mainPage:newAction()
    :title("no helicopter")
    :toggleTitle("yes helicopter")
    :item("red_wool")
    :toggleItem("green_wool")
    :setOnToggle(pings.toggle_heli)
local toggleaction = mainPage:newAction()
    :title("player size")
    :toggleTitle("much bigger size")
    :item("oak_button")
    :toggleItem("oak_planks")
    :setOnToggle(pings.size_change)