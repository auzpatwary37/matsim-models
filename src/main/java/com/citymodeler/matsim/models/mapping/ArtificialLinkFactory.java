package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

import java.util.Set;

/**
 * Creates artificial links (connectors and loops) for unreachable stop transitions.
 */
public final class ArtificialLinkFactory {

    private final Network network;
    private int counter = 0;

    public ArtificialLinkFactory(Network network) {
        this.network = network;
    }

    public Link createConnector(Node fromNode, Node toNode, String mode, String contextId) {
        String linkId = "pt_" + contextId + "_" + (counter++);
        Link link = new Link(
                Id.create(linkId, Link.class),
                fromNode.getId(), toNode.getId(),
                50.0, 900.0, 13.9, 1.0,
                Set.of(mode));
        link.getAttributes().putAttribute("artificial", "true");
        network.addLink(link);
        return link;
    }

    public Link createLoop(Coord coord, String mode, String contextId) {
        String nodeId = "pt_loop_node_" + contextId + "_" + (counter++);
        Node node = new Node(Id.create(nodeId, Node.class), coord);
        network.addNode(node);

        String linkId = "pt_loop_" + contextId + "_" + (counter++);
        Link link = new Link(
                Id.create(linkId, Link.class),
                node.getId(), node.getId(),
                1.0, 900.0, 13.9, 1.0,
                Set.of(mode));
        link.getAttributes().putAttribute("artificial", "true");
        network.addLink(link);
        return link;
    }

    public int getCounter() {
        return counter;
    }
}
