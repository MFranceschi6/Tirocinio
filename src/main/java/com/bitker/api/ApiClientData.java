package com.bitker.api;

import com.bitker.Main;
import com.bitker.eventservice.EventService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by machiara on 03/03/17.
 */
public class ApiClientData {

    private int length;
    private ByteBuffer msgLen;
    private ByteBuffer msg;
    private SocketChannel skt;
    private LinkedBlockingQueue<ByteBuffer []> queue;
    private Set<Long> ids;
    private SelectionKey key;

    ApiClientData(SocketChannel skt){
        length = -1;
        msgLen = ByteBuffer.allocate(4);
        queue = new LinkedBlockingQueue<>();
        this.skt = skt;
        ids = new HashSet<>();
    }

    public void read(){
        try
        {
            if (length == -1)
            {
                if (skt.read(msgLen) == -1)
                    close();
                if (!(msgLen.position() == msgLen.limit()))
                    return;
                msgLen.clear();
                length = msgLen.getInt();
                msgLen.clear();
                msg = ByteBuffer.allocate(length);
            }
            if (skt.read(msg) == -1)
                close();
            if (!(msg.position() == msg.limit()))
                return;
            Main.publicInterface.ex.execute(new PublicInterfaceReader(msg, this));
            msg = null;
            length = -1;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            close();
        }
    }

    private void close() {
        try
        {
            skt.close();
            key.cancel();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        for(Long id : ids)
        {

            EventService.getInstance().unsubscribe(this,id);
        }
    }

    public void write() {
        ByteBuffer [] msg = queue.peek();
        if(msg == null)
        {
            try
            {
                Main.publicInterface.registerChannel(skt,SelectionKey.OP_READ,this);
            } catch (ClosedChannelException e)
            {
                e.printStackTrace();
            }
            return;
        }
        try
        {
            System.out.println("Wrote: "+skt.write(msg));
            System.out.println("last msg position: "+msg[msg.length - 1].position()+" of: "+msg[msg.length - 1].limit());
            if(msg[msg.length - 1].position() == msg[msg.length - 1].limit())
                queue.poll();
            if(queue.isEmpty())
                Main.publicInterface.registerChannel(skt,SelectionKey.OP_READ, this);
        } catch (IOException e)
        {
            e.printStackTrace();
            close();
        }
    }

    public void addMsg(ByteBuffer... param) {
        for(ByteBuffer b : param)
            b.clear();
        queue.add(param);
        try
        {
            Main.publicInterface.registerChannel(skt, SelectionKey.OP_READ | SelectionKey.OP_WRITE,this);
        } catch (IOException e)
        {
            close();
            System.out.println("channel chiuso");
        }
    }

    public synchronized void addId(long id) {
        ids.add(id);
    }

    public void setKey(SelectionKey key) {
        this.key = key;
    }
}