package ez.minar.mixins.network;

import ez.minar.system.events.EventManager;
import ez.minar.system.events.impl.PacketSendEvent;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method = "send(Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Packet<?> onSendModify(Packet<?> packet) {
        if (ez.minar.system.managers.RotationManager.isActive() && packet instanceof net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket interactItem) {
            try {
                return new net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket(
                        interactItem.getHand(),
                        interactItem.getSequence(),
                        ez.minar.system.managers.RotationManager.getCurrentRotation().yaw(),
                        ez.minar.system.managers.RotationManager.getCurrentRotation().pitch()
                );
            } catch (NoSuchMethodError e) {
                try {
                    java.lang.reflect.Method handMethod = interactItem.getClass().getMethod("hand");
                    java.lang.reflect.Method seqMethod = interactItem.getClass().getMethod("sequence");
                    net.minecraft.util.Hand hand = (net.minecraft.util.Hand) handMethod.invoke(interactItem);
                    int sequence = (int) seqMethod.invoke(interactItem);
                    return new net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket(
                            hand,
                            sequence,
                            ez.minar.system.managers.RotationManager.getCurrentRotation().yaw(),
                            ez.minar.system.managers.RotationManager.getCurrentRotation().pitch()
                    );
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return packet;
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSend(Packet<?> packet, CallbackInfo ci) {
        if (ez.minar.utils.helpers.NetworkUtils.silentSending) {
            return;
        }

        PacketSendEvent event = new PacketSendEvent(packet);

        EventManager.call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onReceive(io.netty.channel.ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        ez.minar.system.events.impl.PacketReceiveEvent event = new ez.minar.system.events.impl.PacketReceiveEvent(packet);

        EventManager.call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}