#!/usr/bin/perl

$debug = 0;

sub isZero($$) {
	my($i, $j) = @_;
	@gti = [ @{$gts[$i]} ];
	@gtj = [ @{$gts[$j]} ];

	my($max);
	$max = $#{$gts[$i]};
	print "Analysing $i and $j (max = $max)\n" if $debug;

	for( $ii=0 ; $ii <= $max ; $ii++ ) {
		$prod = $gts[$i][$ii] * $gts[$j][$ii];
		if( $prod > 0 ) { 
			print "\t\t$ii:\t$gts[$i][$ii] * $gts[$j][$ii]\t$prod\n" if $debug;
			return 0; 
		}
	}

	return 1;
}

#-------------------------------------------------------------------------------
# Main
#-------------------------------------------------------------------------------

$| = 1;

print "# Loading data\n";
$l = <STDIN>; # Skip title line
for( $i=0 ; $l = <STDIN> ; $i++ ) {
	chomp $l;
	($chr, $pos, $ref, $alt, $gt) = split /\t/, $l;
	@gt = split '', $gt;
	push @gts, [ @gt ];

	if( $debug ) {
		print "Line $i:\n\t$gt\n\t@gt\n\t" . @gts[$i] . "\n";
	} elsif ( $i % 100 == 0 ) {
		print "\nLine $i:\t.";
	} else {
		print '.';
	}
}
$max = $#gts;
print "# Done: $max lines loaded\n";

# Compute zeros
for( $count=1 ; $count < 10000000 ; $count++ ) {
	$i = int(rand($max + 1));
	$j = int(rand($max + 1));
	$is = isZero($i, $j);
	if( $is ) { $countZero++; }

	if( $count % 100 == 0 ) {
		$perc = 100 * ($countZero / $count);
		print "$countZero / $count\t$perc %\t$i\t$j\n";
	}
}

