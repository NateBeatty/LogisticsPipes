package logisticspipes.commands.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import logisticspipes.commands.LogisticsPipesCommand;
import logisticspipes.commands.abstracts.ICommandHandler;

public class DumpCommand implements ICommandHandler {

    @Override
    public String[] getNames() {
        return new String[] { "dump" };
    }

    @Override
    public boolean isCommandUsableBy(ICommandSender sender) {
        return LogisticsPipesCommand.isOP(sender);
    }

    @Override
    public String[] getDescription() {
        return new String[] { "Dumps the current Tread states", "into the server log" };
    }

    @Override
    public void executeCommand(ICommandSender sender, String[] args) {
        sender.addChatMessage(new ChatComponentText("Dump Created"));
    }
}
