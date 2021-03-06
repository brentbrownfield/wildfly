package org.jboss.as.clustering.jgroups.subsystem;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import org.jboss.as.clustering.controller.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.clustering.jgroups.spi.service.ProtocolStackServiceName;

/**
* Test case for testing sequences of management operations.
*
* @author Richard Achmatowicz (c) 2011 Red Hat Inc.
*/
@RunWith(BMUnitRunner.class)
public class OperationSequencesTestCase extends OperationTestCaseBase {

    // stack test operations
    static final ModelNode addStackOp = getProtocolStackAddOperation("maximal2");
    // addStackOpWithParams calls the operation  below to check passing optional parameters
    //  /subsystem=jgroups/stack=maximal2:add(transport={type=UDP},protocols=[{type=MPING},{type=FLUSH}])
    static final ModelNode addStackOpWithParams = getProtocolStackAddOperationWithParameters("maximal2");
    static final ModelNode removeStackOp = getProtocolStackRemoveOperation("maximal2");

    // transport test operations
    static final ModelNode addTransportOp = getTransportAddOperation("maximal2", "UDP");
    // addTransportOpWithProps calls the operation below to check passing optional parameters
    //   /subsystem=jgroups/stack=maximal2/transport=UDP:add(properties=[{A=>a},{B=>b}])
    static final ModelNode addTransportOpWithProps = getTransportAddOperationWithProperties("maximal2", "UDP");
    static final ModelNode removeTransportOp = getTransportRemoveOperation("maximal2", "UDP");

    // protocol test operations
    static final ModelNode addProtocolOp = getProtocolAddOperation("maximal2", "MPING");
    // addProtocolOpWithProps calls the operation below to check passing optional parameters
    //   /subsystem=jgroups/stack=maximal2:add-protocol(type=MPING, properties=[{A=>a},{B=>b}])
    static final ModelNode addProtocolOpWithProps = getProtocolAddOperationWithProperties("maximal2", "MPING");
    static final ModelNode removeProtocolOp = getProtocolRemoveOperation("maximal2", "MPING");

    @Test
    public void testProtocolStackAddRemoveAddSequence() throws Exception {

        KernelServices services = buildKernelServices();

        ModelNode operation = Operations.createCompositeOperation(addStackOp, addTransportOp, addProtocolOp);

        // add a protocol stack, its transport and a protocol as a batch
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the stack
        result = services.executeOperation(removeStackOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // add the same stack
        result = services.executeOperation(operation);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
    }

    @Test
    public void testProtocolStackRemoveRemoveSequence() throws Exception {

        KernelServices services = buildKernelServices();

        ModelNode operation = Operations.createCompositeOperation(addStackOp, addTransportOp, addProtocolOp);

        // add a protocol stack
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the protocol stack
        result = services.executeOperation(removeStackOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the protocol stack again
        result = services.executeOperation(removeStackOp);
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    /**
     * Tests the ability of the /subsystem=jgroups/stack=X:add() operation
     * to correctly process the optional TRANSPORT and PROTOCOLS parameters.
     */
    @Test
    public void testProtocolStackAddRemoveSequenceWithParameters() throws Exception {

        KernelServices services = buildKernelServices();

        // add a protocol stack specifying TRANSPORT and PROTOCOLS parameters
        ModelNode result = services.executeOperation(addStackOpWithParams);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // check some random values

        // remove the protocol stack
        result = services.executeOperation(removeStackOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the protocol stack again
        result = services.executeOperation(removeStackOp);
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    /**
     * Test for https://issues.jboss.org/browse/WFLY-5290 where server/test hangs when using legacy TRANSPORT alias:
     *
     * Create a simple stack, then remove, re-add a different transport, remove twice expecting the 2nd remove to fail.
     * Tests both situations when stack in inferred from :add operation and when its inferred from the existing resource.
     */
    @Test
    public void testLegacyTransportAliasSequence() throws Exception {

        KernelServices services = buildKernelServices();

        String stackName = "legacyStack";

        // add a sample stack to test legacy paths on
        ModelNode result = services.executeOperation(getProtocolStackAddOperationWithParameters(stackName));
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // add a thread pool
        result = services.executeOperation(getLegacyThreadPoolAddOperation(stackName, "default"));
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        ModelNode op = getLegacyThreadPoolAddOperation(stackName, "default");
        op.get("operation").set("write-attribute");
        op.get("name").set("keepalive-time");
        op.get("value").set(999);
        result = services.executeOperation(op);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        op = Operations.createReadResourceOperation(getSubsystemAddress());
        op.get(ModelDescriptionConstants.INCLUDE_ALIASES).set("true");
        op.get(ModelDescriptionConstants.RECURSIVE).set("true");
        result = services.executeOperation(op);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        op = Util.createOperation(ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION, getSubsystemAddress());
        op.get(ModelDescriptionConstants.INCLUDE_ALIASES).set("true");
        op.get(ModelDescriptionConstants.RECURSIVE).set("true");
        result = services.executeOperation(op);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        result = services.executeOperation(getLegacyTransportRemoveOperation(stackName));
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        result = services.executeOperation(getLegacyTransportAddOperation(stackName, "TCP"));
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        result = services.executeOperation(getLegacyTransportRemoveOperation(stackName));
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        result = services.executeOperation(getLegacyTransportRemoveOperation(stackName));
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    @org.junit.Ignore("This fails for some mysterious reason - but this isn't a critical test")
    @Test
    @BMRule(name="Test remove rollback operation",
            targetClass="org.jboss.as.clustering.jgroups.subsystem.StackRemoveHandler",
            targetMethod="performRuntime",
            targetLocation="AT EXIT",
            action="traceln(\"Injecting rollback fault via Byteman\");$1.setRollbackOnly()")
    public void testProtocolStackRemoveRollback() throws Exception {

        KernelServices services = buildKernelServices();

        ModelNode operation = Operations.createCompositeOperation(addStackOp, addTransportOp, addProtocolOp);

        // add a protocol stack
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the protocol stack
        // the remove has OperationContext.setRollbackOnly() injected
        // and so is expected to fail
        result = services.executeOperation(removeStackOp);
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());

        // need to check that all services are correctly re-installed
        ServiceName channelFactoryServiceName = ProtocolStackServiceName.CHANNEL_FACTORY.getServiceName("maximal2");
        Assert.assertNotNull("channel factory service not installed", services.getContainer().getService(channelFactoryServiceName));
    }
}