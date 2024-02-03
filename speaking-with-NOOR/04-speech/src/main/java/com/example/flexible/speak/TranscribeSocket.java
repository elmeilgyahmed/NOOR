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
import com.google.cloud.texttospeech.v1.*;
// import com.google.cloud.texttospeech.v1.AudioEncoding as TtsAudioEncoding;
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
import com.google.cloud.vertexai.generativeai.preview.ResponseStream;
import com.google.cloud.vertexai.api.GenerationConfig;
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
import java.nio.file.Files;
import java.nio.file.Paths;


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
  // Create a GenerationConfig object



  public TranscribeSocket() {
    gson = new Gson();
  }




      public static void GoogleTextToSpeech(String text) {
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {

            // Build the synthesis input
            SynthesisInput synthesisInput = SynthesisInput.newBuilder().setText(text).build();

            // Build the voice request
            VoiceSelectionParams voice =
                    VoiceSelectionParams.newBuilder()
                            .setLanguageCode("ar-XA") // Adjust the language code as needed
                            .setName("ar-XA-Wavenet-D") // Adjust the voice name as needed
                            .build();

            // Select the type of audio file
            AudioConfig audioConfig =
                    AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.LINEAR16).build();

            // Perform the text-to-speech synthesis
            SynthesizeSpeechResponse response =
                    textToSpeechClient.synthesizeSpeech(synthesisInput, voice, audioConfig);

            // Get the audio content from the response
            ByteString audioContent = response.getAudioContent();
            
            Files.write(
                Paths.get("NOOR/speaking-with-NOOR/04-speech/src/main/webapp/js/output.wav"),
                audioContent.toByteArray(),
                StandardOpenOption.TRUNCATE_EXISTING
            );


            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


        
    // called when we have a message to vertex api
    public void chatDiscussion(String projectId, String location, String modelName, String message) {
        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
                GenerateContentResponse response;
                GenerationConfig generationConfig = GenerationConfig.newBuilder()
                .setTemperature(0.9F)
                .setTopK(16)
                .setTopP(0.1f)
                .setMaxOutputTokens(80)
                .build();
                GenerativeModel model = new GenerativeModel(modelName,generationConfig,vertexAI);
                chatSession = new ChatSession(model);
                response = chatSession.sendMessage("Assume you are Chatbot robot in Zewail city university called NOOR and your are made by a team of reshearchers lead by dr mostafa el shafii shortly answer: ");
                response = chatSession.sendMessage(message);
                /**
                ResponseStream<GenerateContentResponse> responseStream = chatSession.sendMessageStream(message);
                if (responseStream != null) {
                responseStream.forEach(responseItem -> {
                    logger.info("NOOR RESPONSE " + ResponseHandler.getText(responseItem));
                    String vertexResponse = ResponseHandler.getText(responseItem);
                    try {
                            getRemote().sendString(gson.toJson(vertexResponse));
                    } catch (IOException e) {
                        logger.info("IOException while sending string: ");
                // Handle the error appropriately, e.g., close the connection or log the error
                    }
                });}                // ----------------------------------------------
                */
                 // Send the response back to the client
                 String vertexResponse = ResponseHandler.getText(response);

                getRemote().sendString(gson.toJson(vertexResponse));

                // Trigger the 'response' event on the client side
                //getRemote().sendString("{'event': 'response', 'data': '" + gson.toJson(vertexResponse) + "'}");

                logger.info("NOOR RESPONSE " + vertexResponse);
                // -------------
                if (vertexResponse != null){
                    GoogleTextToSpeech(vertexResponse);
                }

                //-----------
        } catch (IOException e) {
        logger.log(Level.WARNING, "Error in Websockts", e);
        }
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
            .setLanguageCode("ar-EG")
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
