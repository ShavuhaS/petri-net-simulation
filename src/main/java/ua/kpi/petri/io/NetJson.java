package ua.kpi.petri.io;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ua.kpi.petri.io.NetDefinition.PlaceDefinition;
import ua.kpi.petri.io.NetDefinition.TransitionDefinition;
import ua.kpi.petri.model.Arc;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;
import ua.kpi.petri.model.Transition;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NetJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private NetJson() {
    }

    public static PetriNet read(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read net file " + path, e);
        }
    }

    public static PetriNet read(InputStream in) {
        try {
            return toNet(MAPPER.readValue(in, NetDefinition.class));
        } catch (JsonProcessingException e) {
            throw new NetFormatException("Invalid net JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PetriNet parse(String json) {
        try {
            return toNet(MAPPER.readValue(json, NetDefinition.class));
        } catch (JsonProcessingException e) {
            throw new NetFormatException("Invalid net JSON: " + e.getOriginalMessage(), e);
        }
    }

    public static void write(PetriNet net, Path path) {
        try {
            MAPPER.writeValue(path.toFile(), toDefinition(net));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write net file " + path, e);
        }
    }

    public static String toJson(PetriNet net) {
        try {
            return MAPPER.writeValueAsString(toDefinition(net));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static PetriNet toNet(NetDefinition definition) {
        require(definition != null, "Net JSON is empty");
        require(definition.name() != null, "Net must have a 'name'");
        require(definition.places() != null, "Net must have a 'places' list");
        require(definition.transitions() != null, "Net must have a 'transitions' list");
        try {
            Map<String, Place> places = new LinkedHashMap<>();
            for (PlaceDefinition pd : definition.places()) {
                require(pd != null, "Place entry must not be null");
                require(pd.name() != null, "Place must have a 'name'");
                Place place = new Place(pd.name(), pd.tokens() == null ? 0 : pd.tokens());
                require(places.putIfAbsent(place.getName(), place) == null,
                        "Duplicate place name: '" + place.getName() + "'");
            }

            List<Transition> transitions = new ArrayList<>();
            for (TransitionDefinition td : definition.transitions()) {
                require(td != null, "Transition entry must not be null");
                require(td.name() != null, "Transition must have a 'name'");
                transitions.add(new Transition(td.name(),
                        toArcs(td.name(), "in", td.in(), places),
                        toArcs(td.name(), "out", td.out(), places)));
            }
            return new PetriNet(definition.name(), new ArrayList<>(places.values()), transitions);
        } catch (IllegalArgumentException e) {
            throw new NetFormatException(e.getMessage(), e);
        }
    }

    public static NetDefinition toDefinition(PetriNet net) {
        List<PlaceDefinition> places = net.getPlaces().stream()
                .map(p -> new PlaceDefinition(p.getName(), p.getInitialTokens()))
                .toList();
        List<TransitionDefinition> transitions = net.getTransitions().stream()
                .map(t -> new TransitionDefinition(t.getName(), toWeights(t.getInputs()), toWeights(t.getOutputs())))
                .toList();
        return new NetDefinition(net.getName(), places, transitions);
    }

    private static List<Arc> toArcs(String transition, String side, Map<String, Integer> weights,
                                    Map<String, Place> places) {
        if (weights == null) {
            return List.of();
        }
        List<Arc> arcs = new ArrayList<>(weights.size());
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            Place place = places.get(entry.getKey());
            require(place != null, "Transition '" + transition + "' has '" + side
                    + "' arc to unknown place '" + entry.getKey() + "'");
            require(entry.getValue() != null, "Transition '" + transition + "' has '" + side
                    + "' arc to place '" + entry.getKey() + "' without weight");
            arcs.add(new Arc(place, entry.getValue()));
        }
        return arcs;
    }

    private static Map<String, Integer> toWeights(List<Arc> arcs) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (Arc arc : arcs) {
            weights.put(arc.place().getName(), arc.weight());
        }
        return weights;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new NetFormatException(message);
        }
    }
}
