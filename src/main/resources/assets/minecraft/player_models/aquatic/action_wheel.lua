local actionHelper = require("actionHelper")
require("script")

-- actions
local page = action_wheel:newPage()
action_wheel:setPage(page)

function tailChange(state, playSound)

	local lamia = state == 1 
	local smallTail = state == 2 or state == 3
	local wrappedTail = state == 3

	MODELPATH.root.SnakeBody:setVisible(lamia)
	MODELPATH.root.UpperBody.Body.Hip.SmallTail:setVisible(smallTail)
	MODELPATH.root.LeftLeg:setVisible(not lamia)
	MODELPATH.root.RightLeg:setVisible(not lamia)
	
	ANIMPATH.tailwrap:setPlaying(state == 3)
	
	animHandle:setState(not lamia and "noL" or nil)
	
	SmallTail.idleXIntensity = wrappedTail and 0.6 or 15
	SmallTail.idleYIntensity = wrappedTail and 0.2 or 5
	SmallTail.velIntensity = wrappedTail and 0.5 or 4
	SmallTail.rotIntensity = wrappedTail and 0.05 or 0.4
	
	SmallTail.enabled = smallTail
	
	LamiaTail:SetEnabled(lamia)

	if player:isLoaded() and playSound then
		sounds["minecraft:item.armor.equip_leather"]:pos(player:getPos()):subtitle("Tail reforms"):pitch(0.7):volume(2):play()
	end
end

TailMultiAction = actionHelper.multi(page, "Tail", 4, tailChange,
{	-- outfit names
	"Full",
	"Small",
	"Small Wrapped",
	"None"
})

function StaffState(state)
	MODELPATH.Arrow:setVisible(not state)
end

StaffToggle = actionHelper.toggle(page, "Staff", StaffState)

function HatState(state, playSound)
	MODELPATH.root.UpperBody.Head.Hat:setVisible(not state)

	if player:isLoaded() and playSound then
		sounds["minecraft:item.armor.equip_leather"]:pos(player:getPos()):subtitle("Outfit Shuffles"):pitch(0.7):volume(2):play()
	end
end

HatToggle = actionHelper.toggle(page, "Hat", HatState)

function HairState(state, playSound)
	MODELPATH.root.UpperBody.Head.Hair.LongHair:setVisible(not state)
	MODELPATH.root.UpperBody.Head.Hair.Bun:setVisible(state)

	if player:isLoaded() and playSound then
		sounds["minecraft:item.armor.equip_leather"]:pos(player:getPos()):subtitle("Hair Rustles"):pitch(1.5):volume(2):play()
	end
end

HairToggle = actionHelper.toggle(page, "Hair Bun", HairState)

PoseAction = actionHelper.pose(page, "Pose", ANIMPATH.pose)

function pings.sync(a,b,c,d)
	TailMultiAction.state = a
	tailChange(a)
	StaffToggle.state = b
	StaffState(b)
	HatState(c)
	HairState(d)
end

if host:isHost() then
	function events.tick()
		if world:getTime()%100 == 0 then
			pings.sync(TailMultiAction.state, StaffToggle.state, HatToggle.state, HairToggle.state)
		end
	end
	
	TailMultiAction.action:setTexture(textures["icons.worm"])
	HatToggle.action:setTexture(textures["icons.witch"])
	
	StaffToggle.action:setTitle("Disable Staff"):setToggleTitle("Enable Staff")
		:setTexture(textures["icons.sword"])
	HairToggle.action:setTexture(textures["icons.arrow_up"])
	PoseAction.action:setTexture(textures["icons.pose"])
end