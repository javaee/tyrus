/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.websocket.platform;

import javax.net.websocket.MessageHandler;

/**
 *
 * @author dannycoward
 */
public class MessageHandlerForBean implements MessageHandler.Text {
//    private Model model;
//    private RemoteEndpoint peer;
//    private boolean isServer;
//    private WebSocketEndpointImpl endpoint;
//
//    public MessageHandlerForBean(Model model, RemoteEndpoint peer, WebSocketEndpointImpl endpoint) {
//        this.model = model;
//        this.peer = peer;
//        this.endpoint = endpoint;
//
//    }
//    public void onMessage(String messageString) {
//        System.out.println("hi there !!");
////        this.endpoint.processMessage(peer, messageString);
//        if (true) return;
//        for (Method m : this.model.getOnMessageMethods()) {
//            // check path...
//            try {
//                WebSocketMessage wsm = m.getAnnotation(WebSocketMessage.class);
//                String dynamicPath = wsm.XdynamicPath();
//
//                if (!endpoint.isServer() || this.endpoint.doesPathMatch(dynamicPath)) {
//
//                    int noOfParameters = m.getParameterTypes().length;
//                    Object decodedMessageObject = this.endpoint.doDecode(messageString, m.getParameterTypes()[0].getName());
//
//                    if (decodedMessageObject != null) {
//                        Object returned = null;
//                        //System.out.println("Invoke " + m.getName() + " on " + this.myBean + " with " + m.getParameterTypes().length + " parameters");
//                        //System.out.println("decoded message object is " + decodedMessageObject);
//                        if (noOfParameters == 1) {
//                            returned = m.invoke(this.model.getBean(), decodedMessageObject);
//                        } else if (noOfParameters == 2) {
//                            if (m.getParameterTypes()[1].equals(String.class)) {
//                                returned = m.invoke(this.model.getBean(), decodedMessageObject, dynamicPath);
//                            } else {
//                                returned = m.invoke(this.model.getBean(), decodedMessageObject, peer);
//                            }
//                        } else if (noOfParameters == 3) {
//                            if (m.getParameterTypes()[1].equals(String.class)) {
//                                returned = m.invoke(this.model.getBean(), decodedMessageObject, dynamicPath, peer);
//                            } else {
//                                returned = m.invoke(this.model.getBean(), decodedMessageObject, peer, dynamicPath);
//                            }
//                        } else {
//                            throw new RuntimeException("can't deal with " + noOfParameters + " parameters.");
//                        }
//                        if (returned != null) {
//                            String messageToSendAsString = this.endpoint.doEncode(returned);
//                            peer.sendString(messageToSendAsString);
////                            one / all messages are called.
////                            break;
//                        }
//                    }
//                }
//            } catch (IOException ioe) {
//                this.endpoint.handleGeneratedBeanException(peer, ioe);
//            } catch (DecodeException ce) {
//                this.endpoint.handleGeneratedBeanException(peer, ce);
//            } catch (Exception ex) {
//                ex.printStackTrace();
//                throw new RuntimeException("Error invoking " + m);
//            }
//        }
//
//    }

    @Override
    public void onMessage(String text) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}