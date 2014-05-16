package com.quickblox.qmunicate.qb.commands;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.quickblox.qmunicate.core.command.ServiceCommand;
import com.quickblox.qmunicate.qb.helpers.QBChatHelper;
import com.quickblox.qmunicate.service.QBService;
import com.quickblox.qmunicate.service.QBServiceConsts;

public class QBSendGroupChatMessageCommand extends ServiceCommand {

    private QBChatHelper chatHelper;

    public QBSendGroupChatMessageCommand(Context context, QBChatHelper ChatHelper, String successAction,
                                         String failAction) {
        super(context, successAction, failAction);
        this.chatHelper = ChatHelper;
    }

    public static void start(Context context, String message) {
        Intent intent = new Intent(QBServiceConsts.SEND_GROUP_MESSAGE_ACTION, null, context, QBService.class);
        intent.putExtra(QBServiceConsts.EXTRA_CHAT_MESSAGE, message);
        context.startService(intent);
        Log.i("GroupMessage: ", "From start, Chat message: " + message);
    }

    @Override
    protected Bundle perform(Bundle extras) throws Exception {
        String message = extras.getString(QBServiceConsts.EXTRA_CHAT_MESSAGE);
        Log.i("GroupMessage: ", "From perform, Chat message: " + message);
        chatHelper.sendGroupMessage(message);
        return null;
    }
}