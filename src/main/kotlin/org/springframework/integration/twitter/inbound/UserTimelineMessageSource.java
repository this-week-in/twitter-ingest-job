package org.springframework.integration.twitter.inbound;

import org.springframework.social.twitter.api.Tweet;
import org.springframework.social.twitter.api.Twitter;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Retrieves the tweets for a particular user since a particular tweet.
 *
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */
public class UserTimelineMessageSource extends AbstractTwitterMessageSource<Tweet> {

	private final String profileId;

	public UserTimelineMessageSource(String profileId, Twitter twitter, String metadataKey) {
		super(twitter, metadataKey);
		this.profileId = profileId;
		Assert.notNull(this.profileId, "the profileId must not be null!");
	}

	@Override
	protected List<Tweet> pollForTweets(long sinceId) {
		return this.getTwitter().timelineOperations().getUserTimeline(this.profileId, this.getPageSize(), sinceId, 0);
	}
}
