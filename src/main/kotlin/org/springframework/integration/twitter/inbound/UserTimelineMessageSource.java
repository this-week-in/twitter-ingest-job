package org.springframework.integration.twitter.inbound;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

	private final Log log = LogFactory.getLog(getClass());

	public UserTimelineMessageSource(String profileId, Twitter twitter, String metadataKey) {
		super(twitter, metadataKey);
		this.profileId = profileId;
		Assert.notNull(this.profileId, "the profileId must not be null!");
	}

	@Override
	protected List<Tweet> pollForTweets(long sinceId) {
		this.log.debug("polling for tweets for @" + this.profileId + ".");
		return this.getTwitter().timelineOperations().getUserTimeline(this.profileId, this.getPageSize(), sinceId, 0);
	}
}
