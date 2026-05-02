package com.coast.handlers.fetch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.coast.supabase.SupabaseClient;
import com.google.gson.JsonParser;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Map;

public class EmailExistsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            Map<String, String> params = event.getQueryStringParameters();
            if (params == null || !params.containsKey("email")) {
                return response(400, "{\"error\":\"Missing email parameter\"}");
            }

            String email = params.get("email");
            String url = SupabaseClient.getUrl() + "/rest/v1/users?email=eq." + email + "&select=user_id";
            Request request = SupabaseClient.baseRequest(url).get().build();

            try (Response res = SupabaseClient.getHttpClient().newCall(request).execute()) {
                String body = res.body().string();
                if (!res.isSuccessful()) return response(500, "{\"error\":\"" + body + "\"}");
                boolean exists = JsonParser.parseString(body).getAsJsonArray().size() > 0;
                return response(200, "{\"exists\":" + exists + "}");
            }

        } catch (Exception e) {
            return response(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(java.util.Map.of(
                        "Access-Control-Allow-Origin", "*",
                        "Access-Control-Allow-Headers", "Content-Type",
                        "Access-Control-Allow-Methods", "GET,POST,PATCH,DELETE,OPTIONS"
                ))
                .withBody(body);
    }
}