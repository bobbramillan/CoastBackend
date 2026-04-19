package com.coast.handlers.insert;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.coast.supabase.SupabaseClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class InsertUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            JsonObject user = JsonParser.parseString(event.getBody()).getAsJsonObject();

            RequestBody body = RequestBody.create(
                    SupabaseClient.getGson().toJson(user),
                    MediaType.parse("application/json")
            );

            Request request = SupabaseClient.baseRequest(SupabaseClient.getUrl() + "/rest/v1/users")
                    .addHeader("Prefer", "return=minimal")
                    .post(body).build();

            try (Response res = SupabaseClient.getHttpClient().newCall(request).execute()) {
                if (!res.isSuccessful()) return response(500, "{\"error\":\"" + res.body().string() + "\"}");
                return response(200, "{\"success\":true}");
            }

        } catch (Exception e) {
            return response(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withBody(body);
    }
}
