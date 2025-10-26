#!/usr/bin/env python3
"""
DMQ Metadata Service - Master Test Runner
Runs all metadata service test scripts in sequence
"""

import os
import sys
import subprocess
import requests
import time
from pathlib import Path

class TestRunner:
    def __init__(self):
        self.total_tests = 0
        self.passed_tests = 0
        self.failed_tests = 0

        # ANSI color codes
        self.colors = {
            'RED': '\033[0;31m',
            'GREEN': '\033[0;32m',
            'YELLOW': '\033[1;33m',
            'BLUE': '\033[0;34m',
            'PURPLE': '\033[0;35m',
            'NC': '\033[0m'  # No Color
        }

    def colorize(self, text, color):
        """Add color to text output"""
        return f"{self.colors[color]}{text}{self.colors['NC']}"

    def print_header(self):
        """Print the test suite header"""
        print("=" * 42)
        print("DMQ Metadata Service - Complete Test Suite")
        print("=" * 42)
        print("Running all metadata service tests...")
        print()

    def run_test(self, test_script, test_name):
        """Run a test script and track results"""
        print(self.colorize("=" * 42, 'BLUE'))
        print(self.colorize(f"Running: {test_name}", 'BLUE'))
        print(self.colorize(f"Script: {test_script}", 'BLUE'))
        print(self.colorize("=" * 42, 'BLUE'))

        script_path = Path(test_script)
        if script_path.exists():
            try:
                # Run the Python script
                result = subprocess.run([sys.executable, test_script], cwd=script_path.parent)
                exit_code = result.returncode

                self.total_tests += 1
                if exit_code == 0:
                    print(self.colorize(f"✓ {test_name} PASSED", 'GREEN'))
                    self.passed_tests += 1
                else:
                    print(self.colorize(f"✗ {test_name} FAILED (exit code: {exit_code})", 'RED'))
                    self.failed_tests += 1
            except Exception as e:
                print(self.colorize(f"✗ Error running {test_name}: {e}", 'RED'))
                self.failed_tests += 1
                self.total_tests += 1
        else:
            print(self.colorize(f"✗ Test script not found: {test_script}", 'RED'))
            self.failed_tests += 1
            self.total_tests += 1

        print()
        print()

    def check_service_availability(self):
        """Check if metadata service is running"""
        print(self.colorize("Checking metadata service availability...", 'YELLOW'))

        try:
            response = requests.get("http://localhost:9091/api/v1/metadata/topics", timeout=5)
            if response.status_code == 200:
                print(self.colorize("✓ Metadata service is running", 'GREEN'))
                return True
            else:
                print(self.colorize(f"✗ Metadata service returned status {response.status_code}", 'RED'))
                return False
        except requests.RequestException as e:
            print(self.colorize(f"✗ Metadata service is not accessible at http://localhost:9091: {e}", 'RED'))
            return False

    def print_pre_test_setup(self):
        """Print pre-test setup information"""
        print(self.colorize("Pre-test Setup:", 'YELLOW'))
        print("1. Ensure metadata service is running on http://localhost:9091")
        print("2. Ensure PostgreSQL is running and accessible")
        print("3. Ensure config/services.json is properly configured")
        print("4. Check that no other processes are using port 9091")
        print()

    def print_final_results(self):
        """Print final test results"""
        print("=" * 42)
        print("TEST SUITE COMPLETE")
        print("=" * 42)
        print()
        print(self.colorize("Final Results:", 'BLUE'))
        print(f"Total Tests: {self.total_tests}")
        print(self.colorize(f"Passed: {self.passed_tests}", 'GREEN'))

        if self.failed_tests > 0:
            print(self.colorize(f"Failed: {self.failed_tests}", 'RED'))
        else:
            print(self.colorize(f"Failed: {self.failed_tests}", 'GREEN'))

        if self.total_tests > 0:
            success_rate = (self.passed_tests * 100) // self.total_tests
        else:
            success_rate = 0
        print(f"Success Rate: {success_rate}%")

        print()
        if self.failed_tests == 0:
            print(self.colorize("🎉 ALL TESTS PASSED! 🎉", 'GREEN'))
            print()
            print("Metadata Service Implementation Status:")
            print("✓ KRaft Consensus: WORKING")
            print("✓ Service Discovery: WORKING")
            print("✓ Metadata Versioning: WORKING")
            print("✓ Heartbeat Processing: WORKING")
            print("✓ Push Synchronization: WORKING")
            print("✓ Topic Management: WORKING")
            print("✓ Broker Registration: WORKING")
        else:
            print(self.colorize("❌ SOME TESTS FAILED", 'RED'))
            print()
            print("Please check the test output above for failure details.")
            print("Common issues:")
            print("- Metadata service not running")
            print("- Database connection issues")
            print("- Port conflicts")
            print("- Configuration problems")

    def print_post_test_steps(self):
        """Print post-test verification steps"""
        print()
        print("=" * 42)
        print("Post-Test Verification Steps:")
        print("=" * 42)
        print()
        print("1. Check Metadata Service Logs:")
        print("   - Look for KRaft consensus messages")
        print("   - Verify heartbeat processing logs")
        print("   - Check push synchronization logs")
        print()
        print("2. Check Storage Service Logs (if running):")
        print("   - Verify receipt of metadata updates")
        print("   - Check heartbeat sending logs")
        print("   - Confirm metadata synchronization")
        print()
        print("3. Verify Database State:")
        print("   - Check PostgreSQL for persisted metadata")
        print("   - Verify topic and broker tables")
        print()
        print("4. Test End-to-End Flow:")
        print("   - Run storage service tests")
        print("   - Test complete producer/consumer flow")
        print()
        print("=" * 42)

    def run_all_tests(self):
        """Run the complete test suite"""
        self.print_header()
        self.print_pre_test_setup()

        # Check service availability
        if not self.check_service_availability():
            print(self.colorize("Please start the metadata service first:", 'YELLOW'))
            print("cd dmq-metadata-service && mvn spring-boot:run")
            sys.exit(1)

        print()

        # Run all test scripts
        self.run_test("test_basic_metadata_operations.py", "Basic Metadata Operations Test")
        self.run_test("test_broker_registration.py", "Broker Registration Test")
        self.run_test("test_heartbeat_processing.py", "Heartbeat Processing Test")
        self.run_test("test_metadata_synchronization.py", "Metadata Synchronization Test")
        self.run_test("test_kraft_consensus.py", "KRaft Consensus Test")

        # Print final results
        self.print_final_results()
        self.print_post_test_steps()


def main():
    """Main entry point"""
    try:
        runner = TestRunner()
        runner.run_all_tests()
    except KeyboardInterrupt:
        print("\nTest suite interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"Unexpected error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()