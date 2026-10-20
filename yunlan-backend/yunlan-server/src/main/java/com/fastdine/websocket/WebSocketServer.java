package com.fastdine.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务端：管理端页面连 /ws/{sid} 收来单提醒与客户催单
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    /**
     * 在线连接：sid（前端生成的会话标识）-> Session
     * 静态是因为每个 WebSocket 连接都会新建一个 WebSocketServer 实例，靠静态集合共享
     */
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        SESSION_MAP.put(sid, session);
        log.info("WebSocket 建立连接：sid={}，当前在线 {} 个", sid, SESSION_MAP.size());
    }

    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到客户端 {} 的消息：{}", sid, message);
    }

    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        SESSION_MAP.remove(sid);
        log.info("WebSocket 断开连接：sid={}，当前在线 {} 个", sid, SESSION_MAP.size());
    }

    /**
     * 广播给所有在线客户端
     *
     * @param message JSON 字符串，{"type":1,"orderId":1,"content":"..."}
     */
    public static void sendToAllClient(String message) {
        for (Map.Entry<String, Session> entry : SESSION_MAP.entrySet()) {
            Session session = entry.getValue();
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (IOException e) {
                log.error("WebSocket 推送失败，sid={}", entry.getKey(), e);
            }
        }
    }
}
