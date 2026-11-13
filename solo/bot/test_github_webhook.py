"""Security tests for the signed GitHub webhook endpoint helpers."""
import hashlib
import hmac
import unittest

import api_server


class GitHubWebhookSecurityTests(unittest.TestCase):
    def test_valid_hmac_is_accepted(self):
        body = b'{"action":"published"}'
        secret = "local-test-secret"
        signature = "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
        self.assertTrue(api_server.verify_github_signature(body, signature, secret))

    def test_invalid_or_missing_hmac_is_rejected(self):
        body = b'{"action":"published"}'
        self.assertFalse(api_server.verify_github_signature(body, "", "secret"))
        self.assertFalse(api_server.verify_github_signature(body, "sha256=bad", "secret"))
        self.assertFalse(api_server.verify_github_signature(body, "sha256=00", ""))

    def test_issue_comment_is_an_accepted_webhook_event(self):
        self.assertTrue(api_server.github_webhook_event_supported("issues"))
        self.assertTrue(api_server.github_webhook_event_supported("issue_comment"))
        self.assertTrue(api_server.github_webhook_event_supported("release"))
        self.assertFalse(api_server.github_webhook_event_supported("push"))

    def test_webhook_repository_allowlist_is_case_insensitive(self):
        self.assertTrue(api_server.github_repository_allowed("JoshBet9/Testplex", "joshbet9/testplex"))
        self.assertFalse(api_server.github_repository_allowed("attacker/testplex", "joshbet9/testplex"))


if __name__ == "__main__":
    unittest.main()
