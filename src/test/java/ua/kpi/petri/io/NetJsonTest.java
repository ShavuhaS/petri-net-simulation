package ua.kpi.petri.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.kpi.petri.model.Arc;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Transition;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetJsonTest {

    private static final String ABC = """
            {
              "name": "abc",
              "places": [
                { "name": "P1", "tokens": 1 },
                { "name": "P2", "tokens": 1 },
                { "name": "P3" },
                { "name": "Gen", "tokens": 1 }
              ],
              "transitions": [
                { "name": "A", "in": { "P1": 1 }, "out": { "P2": 1 } },
                { "name": "C", "in": { "P2": 2 }, "out": { "P3": 1 } },
                { "name": "Arrival", "in": { "Gen": 1 }, "out": { "Gen": 1, "P1": 1 } }
              ]
            }
            """;

    @Test
    void readsPlacesTransitionsAndArcWeights() {
        PetriNet net = NetJson.parse(ABC);

        assertEquals("abc", net.getName());
        assertEquals(4, net.getPlaces().size());
        assertEquals(0, net.findPlace("P3").orElseThrow().getInitialTokens());

        Transition c = net.findTransition("C").orElseThrow();
        Arc in = c.getInputs().getFirst();
        assertEquals("P2", in.place().getName());
        assertEquals(2, in.weight());
        assertEquals(2, net.findTransition("Arrival").orElseThrow().getOutputs().size());
    }

    @Test
    void arcsReferToSamePlaceObjects() {
        PetriNet net = NetJson.parse(ABC);
        Transition a = net.findTransition("A").orElseThrow();
        assertTrue(a.getOutputs().getFirst().place() == net.findPlace("P2").orElseThrow());
    }

    @Test
    void writeThenReadGivesSameNet(@TempDir Path dir) {
        PetriNet original = NetJson.parse(ABC);
        Path file = dir.resolve("net.json");
        NetJson.write(original, file);
        PetriNet copy = NetJson.read(file);

        assertEquals(NetJson.toDefinition(original), NetJson.toDefinition(copy));
    }

    @Test
    void rejectsUnknownPlace() {
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": 0 } ],
                  "transitions": [ { "name": "T", "in": { "Q": 1 } } ] }
                """, "unknown place 'Q'");
    }

    @Test
    void rejectsNonPositiveWeight() {
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": 0 } ],
                  "transitions": [ { "name": "T", "in": { "P": 0 } } ] }
                """, "weight");
    }

    @Test
    void rejectsNegativeTokens() {
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": -1 } ],
                  "transitions": [ { "name": "T", "in": { "P": 1 }, "out": { "P": 1 } } ] }
                """, "negative");
    }

    @Test
    void rejectsDuplicateNames() {
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": 0 }, { "name": "P", "tokens": 1 } ],
                  "transitions": [] }
                """, "Duplicate place");
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": 0 } ],
                  "transitions": [ { "name": "T", "in": { "P": 1 }, "out": { "P": 1 } },
                                   { "name": "T", "in": { "P": 1 }, "out": { "P": 1 } } ] }
                """, "Duplicate transition");
    }

    @Test
    void rejectsTransitionWithoutInputOrOutput() {
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": 0 } ],
                  "transitions": [ { "name": "Source", "out": { "P": 1 } } ] }
                """, "'Source' must have at least one input place and at least one output place");
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": 1 } ],
                  "transitions": [ { "name": "Sink", "in": { "P": 1 }, "out": {} } ] }
                """, "'Sink' must have");
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": 1 } ], "transitions": [] }
                """, "at least one transition");
    }

    @Test
    void rejectsDuplicateArcKeysAndTypos() {
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": 1 } ],
                  "transitions": [ { "name": "T", "in": { "P": 1, "P": 2 }, "out": { "P": 1 } } ] }
                """, "Duplicate");
        assertInvalid("""
                { "name": "n", "places": [ { "name": "P", "tokens": 1 } ],
                  "transitions": [ { "name": "T", "inputs": { "P": 1 } } ] }
                """, "inputs");
    }

    @Test
    void rejectsMissingSections() {
        assertInvalid("{ \"name\": \"n\", \"transitions\": [] }", "places");
        assertInvalid("{ \"places\": [], \"transitions\": [] }", "name");
    }

    private static void assertInvalid(String json, String expectedMessagePart) {
        NetFormatException e = assertThrows(NetFormatException.class, () -> NetJson.parse(json));
        assertTrue(e.getMessage().contains(expectedMessagePart),
                () -> "Expected message to contain '" + expectedMessagePart + "', got: " + e.getMessage());
    }
}
