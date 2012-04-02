/*
 * Copyright 2011 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.model;

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;

import javax.swing.tree.TreePath;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This model holds the source and destination of a node mapping, and is observable.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CreateModel {
    private SipModel sipModel;
    private SortedSet<SourceTreeNode> sourceTreeNodes;
    private RecDefTreeNode recDefTreeNode;
    private NodeMapping nodeMapping;
    private boolean settingNodeMapping;

    public CreateModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    public void setSourceTreeNodes(SortedSet<SourceTreeNode> sourceTreeNodes) {
        if (sourceTreeNodes != null && sourceTreeNodes.isEmpty()) sourceTreeNodes = null;
        this.sourceTreeNodes = sourceTreeNodes;
        setNodeMapping(null);
        for (Listener listener : listeners) listener.statsTreeNodeSet(this);
    }

    public void setRecDefTreePath(Path path, String discriminatorKey) {
        TreePath treePath = sipModel.getMappingModel().getTreePath(path, discriminatorKey);
        RecDefTreeNode node = ((RecDefTreeNode) (treePath.getLastPathComponent()));
        setRecDefTreeNode(node);
    }

    public void setRecDefTreeNode(RecDefTreeNode recDefTreeNode) {
        if (recDefTreeNode != null && recDefTreeNode.getParent() == null) recDefTreeNode = null;
        this.recDefTreeNode = recDefTreeNode;
        setNodeMapping(null);
        for (Listener listener : listeners) listener.recDefTreeNodeSet(this);
    }

    public void setNodeMapping(NodeMapping nodeMapping) {
        if (settingNodeMapping) return;
        this.nodeMapping = nodeMapping;
        if (nodeMapping != null) {
            settingNodeMapping = true;
            setSourceTreeNodes(sipModel.getStatsModel().findNodesForInputPaths(nodeMapping));
            TreePath treePath = sipModel.getMappingModel().getTreePath(nodeMapping.outputPath, nodeMapping.discriminatorKey);
            if (treePath != null) setRecDefTreeNode((RecDefTreeNode) treePath.getLastPathComponent());
            settingNodeMapping = false;
        }
        for (Listener listener : listeners) listener.nodeMappingSet(this);
    }

    public SortedSet<SourceTreeNode> getSourceTreeNodes() {
        return sourceTreeNodes;
    }

    public RecDefTreeNode getRecDefTreeNode() {
        return recDefTreeNode;
    }

    public NodeMapping getNodeMapping() {
        return nodeMapping;
    }

    public void revertToOriginal() {
        if (nodeMapping != null) nodeMapping.setGroovyCode(null);
    }

    public boolean canCreate() {
        if (recDefTreeNode == null || sourceTreeNodes == null) return false;
        if (nodeMapping == null) {
            nextNodeMapping: for (NodeMapping mapping : recDefTreeNode.getRecDefNode().getNodeMappings().values()) {
                Iterator<Path> pathIterator = mapping.getInputPaths().iterator();
                for (Path inputPath : mapping.getInputPaths()) {
                    if (!pathIterator.hasNext() || !inputPath.equals(pathIterator.next())) continue nextNodeMapping;
                }
                nodeMapping = mapping;
                break;
            }
        }
        return nodeMapping == null;
    }
    
    public void createMapping() {
        if (!canCreate()) throw new RuntimeException("Should have checked");
        NodeMapping created = new NodeMapping().setOutputPath(recDefTreeNode.getRecDefPath().getTagPath(), recDefTreeNode.getRecDefNode().getDiscriminatorRootKey());
        created.recDefNode = recDefTreeNode.getRecDefNode();
        SourceTreeNode.setStatsTreeNodes(sourceTreeNodes, created);
        recDefTreeNode.addNodeMapping(created);
        setNodeMapping(created);
    }

    public void createMapping(NodeMapping nodeMapping) {
        TreePath treePath = sipModel.getMappingModel().getTreePath(nodeMapping.outputPath, nodeMapping.discriminatorKey);
        RecDefTreeNode recDefTreeNode = (RecDefTreeNode) treePath.getLastPathComponent();
        nodeMapping.recDefNode = recDefTreeNode.getRecDefNode();
        SourceTreeNode.setStatsTreeNodes(sipModel.getStatsModel().findNodesForInputPaths(nodeMapping), nodeMapping);
        recDefTreeNode.addNodeMapping(nodeMapping);
        setNodeMapping(nodeMapping);
    }

    public boolean isDictionaryPossible() {
        if (nodeMapping == null || recDefTreeNode == null|| !nodeMapping.hasOneStatsTreeNode()) return false;
        SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleStatsTreeNode();
        if (sourceTreeNode.getStatistics() == null) return false;
        Set<String> values = sourceTreeNode.getStatistics().getHistogramValues();
        RecDef.DiscriminatorList options = recDefTreeNode.getRecDefNode().getDiscriminators();
        return values != null && options != null && nodeMapping.dictionary == null;
    }

    public boolean isDictionaryPresent() {
        return nodeMapping != null && nodeMapping.dictionary != null;
    }

    public void createDictionary() {
        if (!isDictionaryPossible()) throw new RuntimeException("Should have checked");
        List<String> options = new ArrayList<String>();
//        for (RecDef.Opt opt : recDefTreeNode.getRecDefNode().getDiscriminators()) options.add(opt.content); // todo: not key?
        nodeMapping.setDictionaryDomain(options);
        fireNodeMappingChanged();
    }

    public int countNonemptyDictionaryEntries() {
        int nonemptyEntries = 0;
        if (isDictionaryPresent()) {
            for (String value : nodeMapping.dictionary.values()) if (!value.trim().isEmpty()) nonemptyEntries++;
        }
        return nonemptyEntries;
    }

    public void removeDictionary() {
        if (isDictionaryPresent()) {
            nodeMapping.dictionary = null;
            fireNodeMappingChanged();
        }
    }

    // observable

    public interface Listener {

        void statsTreeNodeSet(CreateModel createModel);

        void recDefTreeNodeSet(CreateModel createModel);

        void nodeMappingSet(CreateModel createModel);

        void nodeMappingChanged(CreateModel createModel);

    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void fireNodeMappingChanged() {
        for (Listener listener : listeners) listener.nodeMappingChanged(this);
    }
}
