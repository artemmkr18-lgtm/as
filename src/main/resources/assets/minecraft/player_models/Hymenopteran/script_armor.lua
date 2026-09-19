local armor = require("lib/KattArmor")()
armor.Armor.Leggings:setLayer(1)

bug = models.model.bug
head = bug.Torso.AHead
abdomen = bug.Torso.Abdomen

armor.Armor.Helmet:addParts(
head.AHeadArmor,
head.AHeadArmor2,
head.AHeadArmor3
)

armor.Armor.Chestplate:addParts(
bug.Torso.TorsoArmor,
bug.Torso.TorsoArmor2,
bug.Torso.TorsoArmor3,
bug.Torso.TorsoArmor4,
bug.Torso.TorsoArmor5,
abdomen.AbdomenArmor,
abdomen.AbdomenArmor2,
abdomen.AbdomenArmor3,
abdomen.AbdomenArmor4,
abdomen.AbdomenArmor5
)

armor.Armor.Leggings:addParts(
bug.LeftShoulder.LeftLowerArm.LeftLowerArmArmor,
bug.LeftShoulder.LeftLowerArm.LeftHand.LeftHandArmor,
bug.RightShoulder.RightLowerArm.RightLowerArmArmor,
bug.RightShoulder.RightLowerArm.RightHand.RightHandArmor,

bug.LeftFlank.LeftLowerLeg.LeftLowerLegArmor,
bug.LeftFlank.LeftLowerLeg.LeftFoot.LeftFootArmor,
bug.RightFlank.RightLowerLeg.RightLowerLegArmor,
bug.RightFlank.RightLowerLeg.RightFoot.RightFootArmor,

bug.LeftFlank2.LeftLowerLeg2.LeftLowerLeg2Armor,
bug.LeftFlank2.LeftLowerLeg2.LeftFoot2.LeftFoot2Armor,
bug.RightFlank2.RightLowerLeg2.RightLowerLeg2Armor,
bug.RightFlank2.RightLowerLeg2.RightFoot2.RightFoot2Armor

)
--logTable(textures:getTextures())
armor.Materials.leather:setTexture(textures["armor_leather"])
armor.Materials.golden:setTexture(textures["armor_gold"])
armor.Materials.chainmail:setTexture(textures["armor_chainmail"])
armor.Materials.iron:setTexture(textures["armor_iron"])
armor.Materials.diamond:setTexture(textures["armor_diamond"])
armor.Materials.netherite:setTexture(textures["armor_netherite"])