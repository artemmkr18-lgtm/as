animations.larva.wobble:play()
-- play wobbling loop

pivot = models.model.bug.Torso.AHead.RightItemPivot
bigger_pivot = models.model.bug.Torso.AHead.RightItemPivotLarva
bigger_pivot:setScale(2)

function events.tick()
	local item = player:getHeldItem().id == "minecraft:player_head"
	pivot:setVisible(not item)
	bigger_pivot:setVisible(item)
end