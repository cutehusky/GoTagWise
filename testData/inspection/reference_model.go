package smoke

type ReferenceAddress struct {
	Code string
}

type ReferenceBase struct {
	Text    string
	Long    int64
	Age     int
	Enabled bool
	Mode    string
	Address *ReferenceAddress
	Fixed   [2]ReferenceAddress
}
