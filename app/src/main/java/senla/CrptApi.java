package senla;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.time.LocalDate;

import javax.annotation.Nonnull;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class CrptApi {

    private final TimeUnit interval;
    private final int requestLimit;
    private volatile int requestSended = 0;

    private HttpClient client = HttpClient.newBuilder().build();
    private final static URI URI;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Document.class, new DocumentDeserializer())
            .registerTypeAdapter(Document.class, new DocumentSerializer())
            .create();

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
    // can specify more threads => but execute process will be nonordered
    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(1);

    static {
        try {
            URI = new URI("https://ismp.crpt.ru/api/v3/lk/documents/create");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.interval = timeUnit;
        this.requestLimit = requestLimit;

        scheduledExecutor.scheduleWithFixedDelay(() -> requestSended = 0, 0, 1, interval);
    }

    public Future<HttpResponse<String>> createDocument(@Nonnull Document document, @Nonnull String sign) {
        // some aop stuff here maybe (loggin , transactions)

        return doCreateDocument(gson.toJson(document), sign);
    }

    private Future<HttpResponse<String>> doCreateDocument(String document, String sign) {
        return networkExecutor.submit(() -> {
            while (requestSended >= requestLimit) {
            }

            // some encryption with sign or whatever

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI)
                    .POST(BodyPublishers.ofString(document))
                    .build();

            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            requestSended += 1;
            return response;
        });
    }

    static public record Document(
            DocumentDescription description,
            String documentId,
            String documentStatus,
            String documentType,
            boolean importRequest,
            String ownerInn,
            String participantInn,
            String producerInn,
            LocalDate productionDate,
            String productionType,
            List<Product> products,
            LocalDate registrationDate,
            String registrationNumber) implements Serializable {

    }

    static public record DocumentDescription(String participantInn) {

    }

    static public record Product(
            String certificateDocument,
            LocalDate certificateDocumentDate,
            String certificateDocumentNumber,
            String ownerInn,
            String producerInn,
            LocalDate productionDate,
            String tnvedCode,
            String uitCode,
            String uituCode) {
    }

    private static class DocumentSerializer implements JsonSerializer<CrptApi.Document> {

        @Override
        public JsonElement serialize(Document doc,
                Type typeOfSrc,
                JsonSerializationContext context) {
            JsonObject main = new JsonObject();
            JsonObject docDescription = new JsonObject();
            docDescription.addProperty("participantInn", doc.description().participantInn());
            JsonArray docProducts = new JsonArray();

            for (Product product : doc.products()) {
                JsonObject docProduct = new JsonObject();
                docProduct.addProperty("certificate_document", product.certificateDocument());
                docProduct.addProperty("certificate_document_date", product.certificateDocumentDate().toString());
                docProduct.addProperty("certificate_document_number", product.certificateDocumentNumber());
                docProduct.addProperty("owner_inn", product.ownerInn());
                docProduct.addProperty("producer_inn", product.producerInn());
                docProduct.addProperty("production_date", product.productionDate().toString());
                docProduct.addProperty("tnved_code", product.tnvedCode());
                docProduct.addProperty("uit_code", product.uitCode());
                docProduct.addProperty("uitu_code", product.uituCode());
                docProducts.add(docProduct);
            }

            main.add("description", docDescription);
            main.addProperty("doc_id", doc.documentId());
            main.addProperty("doc_status", doc.documentStatus());
            main.addProperty("doc_type", doc.documentType());
            main.addProperty("importRequest", doc.importRequest());
            main.addProperty("owner_inn", doc.ownerInn());
            main.addProperty("participant_inn", doc.participantInn());
            main.addProperty("producer_inn", doc.producerInn());
            main.addProperty("production_date", doc.productionDate().toString());
            main.addProperty("production_type", doc.productionType());
            main.add("products", docProducts);
            main.addProperty("reg_date", doc.registrationDate().toString());
            main.addProperty("reg_number", doc.registrationNumber());

            return main;
        }
    }

    private static class DocumentDeserializer implements JsonDeserializer<CrptApi.Document> {

        @Override
        public Document deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject main = json.getAsJsonObject();

            JsonObject descriptionJson = main.get("description").getAsJsonObject();
            DocumentDescription documentDescription = new DocumentDescription(
                    descriptionJson.get("participantInn").getAsString());
            String documentId = main.get("doc_id").getAsString();
            String documentStatus = main.get("doc_status").getAsString();
            String documentType = main.get("doc_type").getAsString();
            boolean importRequest = main.get("importRequest").getAsBoolean();
            String ownerInn = main.get("owner_inn").getAsString();
            String participantInn = main.get("participant_inn").getAsString();
            String producerInn = main.get("producer_inn").getAsString();
            LocalDate productionDate = LocalDate.parse(
                    main.get("production_date").getAsString());
            String productionType = main.get("production_type").getAsString();
            JsonArray jsonProducts = main.get("products").getAsJsonArray();
            List<Product> products = new ArrayList<>();
            for (JsonElement el : jsonProducts) {
                JsonObject jsonProduct = el.getAsJsonObject();
                String certificateDocument = jsonProduct.get("certificate_document").getAsString();
                LocalDate certificateDocumentDate = LocalDate.parse(
                        jsonProduct.get("certificate_document_date").getAsString());
                String certificateDocumentNumber = jsonProduct.get("certificate_document_number").getAsString();
                String ownerInn_ = jsonProduct.get("owner_inn").getAsString();
                String producerInn_ = jsonProduct.get("producer_inn").getAsString();
                LocalDate productionDate_ = LocalDate.parse(jsonProduct.get("production_date").getAsString());
                String tnvedCode = jsonProduct.get("tnved_code").getAsString();
                String uitCode = jsonProduct.get("tnved_code").getAsString();
                String uituCode = jsonProduct.get("uitu_code").getAsString();
                products.add(
                        new Product(
                                certificateDocument,
                                certificateDocumentDate,
                                certificateDocumentNumber,
                                ownerInn_,
                                producerInn_,
                                productionDate_,
                                tnvedCode,
                                uitCode,
                                uituCode));
            }

            LocalDate registrationDate = LocalDate.parse(main.get("reg_date").getAsString());
            String registrationNumber = main.get("reg_number").getAsString();

            return new Document(
                    documentDescription,
                    documentId,
                    documentStatus,
                    documentType,
                    importRequest,
                    ownerInn,
                    participantInn,
                    producerInn,
                    productionDate,
                    productionType,
                    products,
                    registrationDate,
                    registrationNumber);
        }
    }
}