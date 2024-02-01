/**
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.flexible.speak;

import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.preview.ChatSession;
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel;
import com.google.cloud.vertexai.generativeai.preview.ResponseHandler;
import io.grpc.auth.ClientAuthInterceptor;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class TranscribeSocket extends WebSocketAdapter
    implements ApiStreamObserver<StreamingRecognizeResponse> {

  private static final Logger logger = Logger.getLogger(TranscribeSocket.class.getName());
  ApiStreamObserver<StreamingRecognizeRequest> requestObserver;
  private Gson gson;
  SpeechClient speech;
  /** fucntion to handle vertexapi connections */
  // TODO(developer): Replace these variables before running the sample.
  String projectId = "noor2-344811";
  String location = "us-central1";
  String modelName = "gemini-pro";
  private boolean hasRun = false;
  List<String> wordsList = new ArrayList<>();
  private ChatSession chatSession; // Assuming chatSession is needed across multiple invocations
  // Initialize client that will be used to send requests. This client only needs
  // to be created once, and can be reused for multiple requests.


  public TranscribeSocket() {
    gson = new Gson();
  }
    // called when we have a message to vertex api
    public void chatDiscussion(String projectId, String location, String modelName, String message) {
        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
                GenerateContentResponse response;
                GenerativeModel model = new GenerativeModel(modelName, vertexAI);
                chatSession = new ChatSession(model);
                response = chatSession.sendMessage("Assume you are Chatbot robot in Zewail city university called NOOR and your are made by a team of reshearchers lead by dr mostafa el shafii shortly answer: ");
                response = chatSession.sendMessage(message);
                logger.info("NOOR RESPONSE " + ResponseHandler.getText(response));
                // ----------------------------------------------
                getRemote().sendString("{'event': 'response', 'data': '" + gson.toJson(response) + "'}");

                // Trigger the 'response' event on the client side
                 // getRemote().sendString("{'event': 'response', 'data': '" + gson.toJson( ResponseHandler.getText(response)) + "'}");
                // ----------------------------------------------    
                //GenerateContentResponse response = chatSession.sendMessage(message);
                //logger.info("SECOND RUN RESPONSE " + ResponseHandler.getText(response));
        } catch (IOException e) {
        logger.log(Level.WARNING, "Error in Websockts", e);
        }
    }

  /**
   * Called to add incoming transcripts to one whole message .
   */        
public  String arrayToString(List<String> list) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < list.size(); i++) {
            result.append(list.get(i));

            // Add a space between words, except for the last word
            if (i < list.size() - 1) {
                result.append(" ");
            }
        }

        return result.toString();
    }
   public  String arrayToStringWithoutDuplicates(List<String> list) {
        List<String> uniqueList = removeDuplicates(list);
        return arrayToString(uniqueList);
    }

    private  List<String> removeDuplicates(List<String> list) {
        Set<String> uniqueSet = new LinkedHashSet<>(list);
        return List.copyOf(uniqueSet);
    }     

  /**
   * Called when the client sends this server some raw bytes (ie audio data).
   */
  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len) {
    if (isConnected()) {
      StreamingRecognizeRequest request =
          StreamingRecognizeRequest.newBuilder()
              .setAudioContent(ByteString.copyFrom(payload, offset, len))
              .build();
      requestObserver.onNext(request);
    }
  }

  /**
   * Called when the client sends this server some text.
   */
  @Override
  public void onWebSocketText(String message) {
    if (isConnected()) {
      Constraints constraints = gson.fromJson(message, Constraints.class);
      logger.info(String.format("Got sampleRate: %s", constraints.sampleRate));

      try {
        speech = SpeechClient.create();
        BidiStreamingCallable<StreamingRecognizeRequest, StreamingRecognizeResponse> callable =
            speech.streamingRecognizeCallable();

        requestObserver = callable.bidiStreamingCall(this);
        // Build and send a StreamingRecognizeRequest containing the parameters for
        // processing the audio.
        RecognitionConfig config =
            RecognitionConfig.newBuilder()
            .setEncoding(AudioEncoding.LINEAR16)
            .setSampleRateHertz(constraints.sampleRate)
            .setLanguageCode("en-US")
            .build();
        StreamingRecognitionConfig streamingConfig =
            StreamingRecognitionConfig.newBuilder()
            .setConfig(config)
            .setInterimResults(true)
            .setSingleUtterance(false)
            .build();

        StreamingRecognizeRequest initial =
            StreamingRecognizeRequest.newBuilder().setStreamingConfig(streamingConfig).build();
        requestObserver.onNext(initial);

        getRemote().sendString(message);
      } catch (IOException e) {
        logger.log(Level.WARNING, "Error in Websockts", e);
      }
    }
  }

  public void closeApiChannel() {
    speech.close();
  }

  /**
   * Called when the connection to the client is closed.
   */
  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    logger.info("Websocket close.");
    requestObserver.onCompleted();
    closeApiChannel();
  }

  /**
   * Called if there's an error connecting with the client.
   */
  @Override
  public void onWebSocketError(Throwable cause) {
    logger.log(Level.WARNING, "Websocket error", cause);
    requestObserver.onError(cause);
    closeApiChannel();
  }


  /**
   * Called when the Speech API has a transcription result for us.
   */
  @Override
  public void onNext(StreamingRecognizeResponse response) {
    List<StreamingRecognitionResult> results = response.getResultsList();
    if (results.size() < 1) {
      return;
    }

    try {
      StreamingRecognitionResult result = results.get(0);
      //logger.info("Got result " + chatGPT(result);
      String transcript = result.getAlternatives(0).getTranscript();
      getRemote().sendString(gson.toJson(result));
      if (result.getIsFinal()){
            //wordsList.add(transcript);
          logger.info("Incoming Transcipt " + result.getAlternatives(0).getTranscript());  
          chatDiscussion(projectId,location,modelName,transcript);
        }
        /**
        else(wordsList.size() <= 20){
            String message = arrayToStringWithoutDuplicates(wordsList);
            logger.info("Completed Sentence " + message);
            try{
            chatDiscussion(projectId,location,modelName,message);
            wordsList.clear();
            } catch (Exception e) {
              logger.log(Level.WARNING, "Error sending to Vertex", e);
         if (e instanceof io.grpc.StatusRuntimeException) {
        io.grpc.StatusRuntimeException grpcException = (io.grpc.StatusRuntimeException) e;
        logger.log(Level.WARNING, "gRPC Status: " + grpcException.getStatus());
        }  
        }
        */  
      // Perform the action every 10 times
      // Reset the counter
    } catch (IOException e) {
      logger.log(Level.WARNING, "Error sending to websocket", e);  
    }
  }

  /**
   * Called if the API call throws an error.
   */
  @Override
  public void onError(Throwable error) {
    logger.log(Level.WARNING, "recognize failed", error);
    // Close the websocket
    getSession().close(500, error.toString());
    closeApiChannel();
  }

  /**
   * Called when the API call is complete.
   */
  @Override
  public void onCompleted() {
    logger.info("recognize completed.");
    // Close the websocket
    getSession().close();
    closeApiChannel();
  }

  // Taken wholesale from StreamingRecognizeClient.java
  private static final List<String> OAUTH2_SCOPES =
      Arrays.asList("https://www.googleapis.com/auth/cloud-platform");
}
