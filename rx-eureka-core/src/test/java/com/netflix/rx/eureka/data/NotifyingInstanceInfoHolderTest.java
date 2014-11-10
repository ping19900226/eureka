package com.netflix.rx.eureka.data;

import com.netflix.rx.eureka.datastore.NotificationsSubject;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.registry.SampleInstanceInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author David Liu
 */
public class NotifyingInstanceInfoHolderTest {

    NotificationsSubject<InstanceInfo> notificationSubject;

    @Rule
    public final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            notificationSubject = NotificationsSubject.create();
        }

    };

    @Test
    public void testUpdateSameSource() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo firstInfo = builder
                .withStatus(InstanceInfo.Status.STARTING)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(notificationSubject, firstInfo.getId());
        holder.update(Source.localSource(), firstInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(1));
        assertThat(holder.getSnapshot().getData(), equalTo(firstInfo));

        InstanceInfo secondInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        holder.update(Source.localSource(), secondInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(1));
        assertThat(holder.getSnapshot().getData(), not(equalTo(firstInfo)));
        assertThat(holder.getSnapshot().getData(), equalTo(secondInfo));
    }

    @Test
    public void testUpdateDifferentSources() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo firstInfo = builder
                .withStatus(InstanceInfo.Status.STARTING)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(notificationSubject, firstInfo.getId());
        holder.update(Source.localSource(), firstInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(1));
        assertThat(holder.getSnapshot().getData(), equalTo(firstInfo));

        InstanceInfo secondInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        Source fooSource = Source.replicationSource("foo");
        holder.update(fooSource, secondInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(2));
        assertThat(holder.getSnapshot().getData(), equalTo(firstInfo));
        assertThat(holder.getSnapshot().getData(), not(equalTo(secondInfo)));

        assertThat(holder.getCopyForSource(fooSource), equalTo(secondInfo));

        InstanceInfo thirdInfo = builder
                .withStatus(InstanceInfo.Status.DOWN)
                .build();

        holder.update(fooSource, thirdInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(2));
        assertThat(holder.getSnapshot().getData(), equalTo(firstInfo));
        assertThat(holder.getSnapshot().getData(), not(equalTo(secondInfo)));

        assertThat(holder.getCopyForSource(fooSource), equalTo(thirdInfo));
    }

    @Test
    public void testRemoveSameSource() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo firstInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(notificationSubject, firstInfo.getId());
        holder.update(Source.localSource(), firstInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(1));
        assertThat(holder.getSnapshot().getData(), equalTo(firstInfo));

        holder.remove(Source.localSource()).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(0));
        assertThat(holder.getSnapshot(), equalTo(null));
        assertThat(holder.getCopyForSource(Source.localSource()), equalTo(null));
    }

    @Test
    public void testRemoveNonSnapshotCopy() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo localInfo = builder
                .withStatus(InstanceInfo.Status.STARTING)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(notificationSubject, localInfo.getId());
        holder.update(Source.localSource(), localInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(1));
        assertThat(holder.getSnapshot().getData(), equalTo(localInfo));

        InstanceInfo fooInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        Source fooSource = Source.replicationSource("foo");
        holder.update(fooSource, fooInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(2));
        assertThat(holder.getSnapshot().getData(), equalTo(localInfo));
        assertThat(holder.getSnapshot().getData(), not(equalTo(fooInfo)));

        assertThat(holder.getCopyForSource(fooSource), equalTo(fooInfo));

        holder.remove(fooSource).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(1));
        assertThat(holder.getSnapshot().getData(), equalTo(localInfo));
        assertThat(holder.getCopyForSource(fooSource), equalTo(null));
    }

    @Test
    public void testRemoveSnapshotCopyPromoteAnother() throws Exception {
        InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();
        InstanceInfo localInfo = builder
                .withStatus(InstanceInfo.Status.STARTING)
                .build();

        NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(notificationSubject, localInfo.getId());
        holder.update(Source.localSource(), localInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(1));
        assertThat(holder.getSnapshot().getData(), equalTo(localInfo));

        InstanceInfo fooInfo = builder
                .withStatus(InstanceInfo.Status.UP)
                .build();

        Source fooSource = Source.replicationSource("foo");
        holder.update(fooSource, fooInfo).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(2));
        assertThat(holder.getSnapshot().getData(), equalTo(localInfo));
        assertThat(holder.getSnapshot().getData(), not(equalTo(fooInfo)));

        assertThat(holder.getCopyForSource(fooSource), equalTo(fooInfo));

        holder.remove(Source.localSource()).toBlocking().firstOrDefault(null);

        assertThat(holder.numCopies(), equalTo(1));
        assertThat(holder.getSnapshot().getData(), equalTo(fooInfo));
        assertThat(holder.getCopyForSource(fooSource), equalTo(fooInfo));
        assertThat(holder.getCopyForSource(Source.localSource()), not(equalTo(localInfo)));
    }

    @Test
    public void testSendEmptyHolderToExpiryQueue() {
        // TODO
    }

    @Test
    public void testRecoverUpdatedEmptyHolderFromExpiryQueue() {
        // TODO
    }
}
