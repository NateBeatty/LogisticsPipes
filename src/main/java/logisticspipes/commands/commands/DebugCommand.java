package logisticspipes.commands.commands;

import java.util.Arrays;

import net.minecraft.command.ICommandSender;

import logisticspipes.LPConstants;
import logisticspipes.commands.abstracts.SubCommandHandler;
import logisticspipes.commands.commands.debug.*;

public class DebugCommand extends SubCommandHandler {

    private static final String[] allowedPlayers = new String[] { "davboecki", "theZorro266" };

    @Override
    public String[] getNames() {
        return new String[] { "debug" };
    }

    @Override
    public boolean isCommandUsableBy(ICommandSender sender) {
        return LPConstants.DEBUG || Arrays.asList(DebugCommand.allowedPlayers).contains(sender.getCommandSenderName());
    }

    @Override
    public String[] getDescription() {
        return new String[] { "Debug helper commands" };
    }

    @Override
    public void registerSubCommands() {
        registerSubCommand(new MeCommand());
        registerSubCommand(new TargetCommand());
        registerSubCommand(new RoutingTableCommand());
        registerSubCommand(new PipeCommand());
        registerSubCommand(new HandCommand());
    }
}
