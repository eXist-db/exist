# Community Code Review and Merge Policy for the [exist-db/exist](https://github.com/exist-db/exist) Git Repository

**Version:** 2.0.0 (2022-08-01)

**NOTE:** This policy is ONLY concerned with the [exist-db/exist](https://github.com/exist-db/exist) Git repository. Other repositories of the eXist-db project are free to adopt and/or adapt it, but there is no obligation to do so.

## Policy

1. As an Open Source project, there is no obligation on the part of the eXist-db project that any PR (Pull Request) will be accepted and merged into the code-base. Best efforts will be made to Review and Merge contributions inline with the policy set out within this document.

2. Members of the community other than the Author or Reviewer(s) are explicitly encouraged to contribute to the review process by testing and commenting on the PR. Concerns raised in such comments must not be disregarded by the Reviewer(s) but should be answered appropriately and considered in the decision.

3. An Author of a PR (Pull Request) *must* **never** Merge their own PRs.

4. A PR *must* always have at least 1 review from a Reviewer who is a [Core Team](#existdb-core-team) member of the [exist-db/exist](https://github.com/exist-db/exist) repository.
    1. Any such [Core Team](#existdb-core-team) member who volunteers to act as a Reviewer on a particular PR *must* be prepared to see it through to completion (i.e. Merged), unless they otherwise indicate within the Review Comments that they have resigned as Reviewer of a particular PR.
    2. If there is only a single Reviewer from the [Core Team](#existdb-core-team), and they resign from the review of a particular PR, a new Reviewer from the [Core Team](#existdb-core-team) *must* be sought by the Author.

5. A PR *should* have at least 2 reviewers.
    1. If after 5 days (Mon-Fri days) there is no second review, then review from one Reviewer will suffice. This grace period allows time for any other interested party to also contribute a review and/or object to the PR.

6. All PRs *must* be subjected to the same quality standards by the Reviewer.
    1. The Reviewer *must* act in the interests of the eXist-db Open Source project and not any personal or private affiliations.

7. The Review process is an iterative process entered into by the Author and Reviewer(s).
    1. Firstly, if clarification is needed, the Author *must* enter into a discussion of the Review comments with the Reviewer(s).
    2. Secondly, assuming agreement between the Author and Reviewer(s), the Author *must* take appropriate action to address the comments from the Review.
    3. This process *should* be carried out publicly.
    4. All decisions *must* be documented within the comments of the PR itself.

8. Remediation process:
    1. In case of
        1. a disagreement between Author and Reviewer(s),
        2. between Reviewers,
        3. or if there has not been within 14 days a reaction
            1. by the author to a blocking review, or
            2. no response by a blocking core developer to a response by the author
        the PR is considered stale. A stale PR *must* be discussed in an upcoming community call where both Author(s) and blocking Reviewer(s) *must* be present.
    2. Should there not be a solution in said call or no call where Author(s) and Reviewer(s) can both be present, a mediator *must* be chosen from a community call. This mediator is announced via Slack and the PR comments. The person chosen will collect feedback by all parties involved, try to find a solution and report back to the community call and other communication channels.
    3. Should the remediation process fail, a vote between the [Core Team](#existdb-core-team) members of the exist-db/exist repository *should* be solicited for a majority result. Any [Core Team](#existdb-core-team) member voting against the PR *must* provide feedback for the reason to the Author; thus allowing the Author to further consider revising their PR.


## Core Team members of the [exist-db/exist](https://github.com/exist-db/exist) Git repository
<a name="existdb-core-team" id="existdb-core-team"></a>
These members have been chosen based on their past contributions to the [exist-db/exist](https://github.com/exist-db/exist) repository and experience. They have been identified as having the necessary skills to review code submitted for inclusion in the [exist-db/exist](https://github.com/exist-db/exist) Git repository (i.e. they are experienced Java Developers with a track record of improving eXist-db).

1. [Juri Leino](https://github.com/line-o)
2. [Wolfgang Meier](https://github.com/wolfgangmm)
3. [Leif-Joran Olsson](https://github.com/ljo)
4. [Patrick Reinhart](https://github.com/reinhapa)
5. [Adam Retter](https://github.com/adamretter)
6. [Dannes Wessels](https://github.com/dizzzz)

This list should be considered current, that is to say that it is not static. Any contributor to eXist-db who demonstrated ability and longevity may apply to join the Core Team. Appointment to the Core Team is approved by a majority vote of the existing Core Team members.
