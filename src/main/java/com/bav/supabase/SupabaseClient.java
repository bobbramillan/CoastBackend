package com.coast.supabase;

import com.google.gson.*;
import com.google.gson.JsonNull;
import okhttp3.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.io.IOException;

public class SupabaseClient {

    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static final String SUPABASE_URL;
    private static final String SUPABASE_API_KEY;

    static {
        SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.US_EAST_1)
                .build();

        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId("coast/supabase")
                .build();

        GetSecretValueResponse response = client.getSecretValue(request);
        JsonObject secret = JsonParser.parseString(response.secretString()).getAsJsonObject();

        SUPABASE_URL     = secret.get("SUPABASE_URL").getAsString();
        SUPABASE_API_KEY = secret.get("SUPABASE_API_KEY").getAsString();

        client.close();
    }

    private SupabaseClient() {}

    public static Request.Builder baseRequest(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_API_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_API_KEY)
                .addHeader("Content-Type", "application/json");
    }

    public static String getUrl() { return SUPABASE_URL; }

    public static OkHttpClient getHttpClient() { return httpClient; }

    public static Gson getGson() { return gson; }
}
