#!/usr/bin/perl -- # --*-Perl-*--

use strict;
use English;
use Getopt::Std;
use vars qw($opt_p $opt_q $opt_u $opt_m);

my $usage = "Usage: $0 [-q] [-u|-p|-m] file [ file ... ]\n";

die $usage if ! getopts('qupm');

die $usage if ($opt_p + $opt_u + $opt_m) != 1;

my $file = shift @ARGV || die $usage;

my $opt = '-u' if $opt_u;
$opt = '-p' if $opt_p;
$opt = '-m' if $opt_m;

while ($file) {
    print "Converting $file to $opt linebreaks.\n" if !$opt_q;
    open (F, "$file");
    binmode F;
    read (F, $_, -s $file);
    close (F);

    s/\r\n/\n/sg;
    s/\r/\n/sg;

    if ($opt eq '-p') {
	s/\n/\r\n/sg;
    } elsif ($opt eq '-m') {
	s/\n/\r/sg;
    }

    open (F, ">$file");
    binmode F;
    print F $_;
    close (F);

    $file = shift @ARGV;
}
